package com.autopanel.feature.script

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.domain.ScriptDraftUploadResult
import com.autopanel.core.domain.SubscriptionRepository
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.model.SubscriptionDraft
import com.autopanel.core.model.SubscriptionInfo
import com.autopanel.core.model.toDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.inject.Inject

private const val INLINE_EDITOR_MAX_BYTES = 512L * 1024L
private const val EXTERNAL_EDITOR_MAX_BYTES = 10L * 1024L * 1024L
private const val MAX_IMPORTED_SCRIPT_BYTES = 10 * 1024 * 1024
private const val SUBSCRIPTION_LOG_CHUNK_BYTES = 64 * 1024
private const val SUBSCRIPTION_LOG_POLL_MS = 2_000L

@HiltViewModel
class ScriptViewModel @Inject constructor(
    private val scriptRepo: ScriptRepository,
    private val subscriptionRepo: SubscriptionRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScriptUiState())
    val uiState: StateFlow<ScriptUiState> = _uiState.asStateFlow()

    private val _events = Channel<ScriptEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var subscriptionLogJob: Job? = null
    private var olderSubscriptionLogJob: Job? = null
    private var contentJob: Job? = null
    private var pageJob: Job? = null
    private var currentScript: ScriptFile? = null

    init { loadScripts() }

    // ── 列表加载 ──

    fun loadScripts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoading = true) }
            scriptRepo.getCachedScripts()?.let { cached ->
                _uiState.update {
                    it.copy(scripts = cached, isLoading = false)
                }
            }
            scriptRepo.getScripts()
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            scripts = list,
                            isRefreshing = false,
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, isLoading = false)
                    }
                    _events.trySend(ScriptEvent.Message(e.message ?: "加载失败"))
                }
        }
    }

    fun selectSection(section: ScriptSection) {
        _uiState.update { it.copy(section = section) }
        if (section == ScriptSection.SUBSCRIPTIONS && !_uiState.value.hasLoadedSubscriptions) {
            loadSubscriptions()
        }
    }

    fun refresh() {
        if (_uiState.value.section == ScriptSection.SCRIPTS) loadScripts()
        else loadSubscriptions(isRefresh = true)
    }

    // ── 查看/编辑内容 ──

    fun loadContent(filename: String, path: String) = loadContent(
        ScriptFile(title = filename, parent = path, type = "file")
    )

    fun loadContent(script: ScriptFile) {
        if (script.isDirectory || script.title.isNullOrBlank()) return
        contentJob?.cancel()
        pageJob?.cancel()
        currentScript = script
        contentJob = viewModelScope.launch {
            _uiState.value.draft?.let { scriptRepo.discardDraft(it) }
            _uiState.update {
                it.copy(
                    editingFilename = script.title.orEmpty(),
                    editingPath = script.parent.orEmpty(),
                    editContent = "",
                    originalContent = "",
                    isLoadingContent = true,
                    contentLoadFailed = false,
                    contentWarning = null,
                    isContentReadOnly = false,
                    showContent = true,
                    contentMode = ScriptContentMode.INLINE,
                    draft = null,
                    previewPage = null,
                    isLoadingPreviewPage = false,
                    hasLocalDraftChanges = false,
                    showOverwriteConfirm = false,
                    showDiscardDraftConfirm = false
                )
            }
            val prepared = scriptRepo.prepareDraft(script)
            val draft = prepared.getOrNull()
            if (draft == null) {
                showContentLoadFailure(prepared.exceptionOrNull())
                return@launch
            }
            when {
                !draft.isUtf8Valid -> showUnavailableDraft(draft)
                draft.sizeBytes <= INLINE_EDITOR_MAX_BYTES -> showInlineDraft(draft)
                else -> showPagedDraft(draft)
            }
        }
    }

    fun closeContent() {
        val state = _uiState.value
        if (state.hasLocalDraftChanges ||
            (state.isEditing && state.editContent != state.originalContent)
        ) {
            _uiState.update { it.copy(showDiscardDraftConfirm = true) }
        } else {
            discardAndCloseContent()
        }
    }

    fun dismissDiscardDraftConfirm() {
        _uiState.update { it.copy(showDiscardDraftConfirm = false) }
    }

    fun confirmDiscardDraft() {
        _uiState.update { it.copy(showDiscardDraftConfirm = false) }
        discardAndCloseContent()
    }

    fun enterEditMode() {
        val state = _uiState.value
        if (state.contentLoadFailed || state.isContentReadOnly) {
            _events.trySend(ScriptEvent.Message(state.contentWarning ?: "脚本内容尚未成功加载，不能编辑"))
        } else if (state.contentMode != ScriptContentMode.INLINE) {
            _events.trySend(ScriptEvent.Message("大文件请使用本地编辑器修改后回传"))
        } else {
            _uiState.update { it.copy(isEditing = true) }
        }
    }

    fun onContentChanged(content: String) {
        _uiState.update {
            it.copy(
                editContent = content,
                hasLocalDraftChanges = content != it.originalContent
            )
        }
    }

    fun saveContent() {
        val s = _uiState.value
        val draft = s.draft ?: return
        if (s.contentLoadFailed || s.isContentReadOnly || s.isSavingContent) return
        _uiState.update { it.copy(isSavingContent = true) }
        viewModelScope.launch {
            val localDraft = if (s.contentMode == ScriptContentMode.INLINE) {
                val replaced = scriptRepo.replaceDraftText(draft, s.editContent, s.hasUtf8Bom)
                replaced.getOrNull() ?: run {
                    _uiState.update { it.copy(isSavingContent = false) }
                    _events.trySend(ScriptEvent.Message(replaced.exceptionOrNull()?.message ?: "保存失败"))
                    return@launch
                }
            } else {
                draft
            }
            uploadDraft(localDraft, force = false)
        }
    }

    fun confirmOverwriteDraft() {
        val draft = _uiState.value.draft ?: return
        _uiState.update { it.copy(showOverwriteConfirm = false, isSavingContent = true) }
        viewModelScope.launch { uploadDraft(draft, force = true) }
    }

    fun dismissOverwriteDraft() {
        _uiState.update { it.copy(showOverwriteConfirm = false, isSavingContent = false) }
    }

    fun previousPreviewPage() {
        val page = _uiState.value.previewPage ?: return
        loadPreviewPage(page.index - 1)
    }

    fun nextPreviewPage() {
        val page = _uiState.value.previewPage ?: return
        loadPreviewPage(page.index + 1)
    }

    fun onExternalEditorReturned() {
        val draft = _uiState.value.draft ?: return
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPreviewPage = true) }
            val refreshedResult = scriptRepo.refreshDraft(draft)
            val refreshed = refreshedResult.getOrNull()
            if (refreshed == null) {
                _uiState.update { it.copy(isLoadingPreviewPage = false) }
                _events.trySend(ScriptEvent.Message(refreshedResult.exceptionOrNull()?.message ?: "读取本地修改失败"))
                return@launch
            }
            if (!refreshed.isUtf8Valid) {
                _uiState.update {
                    it.copy(
                        draft = refreshed,
                        contentMode = ScriptContentMode.UNAVAILABLE,
                        isContentReadOnly = true,
                        contentWarning = "本地编辑后的文件不是有效的 UTF-8 文本，已阻止回传。",
                        isLoadingPreviewPage = false,
                        hasLocalDraftChanges = true
                    )
                }
                return@launch
            }
            val changesResult = scriptRepo.hasDraftChanges(refreshed)
            val changed = changesResult.getOrNull()
            if (changed == null) {
                _uiState.update { it.copy(draft = refreshed, isLoadingPreviewPage = false) }
                _events.trySend(
                    ScriptEvent.Message(changesResult.exceptionOrNull()?.message ?: "检查本地修改失败")
                )
                return@launch
            }
            val requestedPage = _uiState.value.previewPage?.index ?: 0
            val pageResult = scriptRepo.readDraftPage(refreshed, requestedPage)
            val page = pageResult.getOrNull()
            val exceedsEditableLimit = refreshed.sizeBytes > EXTERNAL_EDITOR_MAX_BYTES
            _uiState.update {
                it.copy(
                    draft = refreshed,
                    previewPage = page,
                    editContent = page?.content.orEmpty(),
                    hasUtf8Bom = refreshed.hasUtf8Bom,
                    hasLocalDraftChanges = changed,
                    isContentReadOnly = exceedsEditableLimit,
                    isLoadingPreviewPage = false,
                    contentLoadFailed = page == null,
                    contentWarning = if (exceedsEditableLimit) {
                        pagedDraftMessage(refreshed)
                    } else if (changed) {
                        "已检测到本地修改。上传前会检查服务端脚本是否同时发生变化。"
                    } else {
                        pagedDraftMessage(refreshed)
                    }
                )
            }
            if (page == null) {
                _events.trySend(ScriptEvent.Message(pageResult.exceptionOrNull()?.message ?: "读取本地修改失败"))
            }
        }
    }

    fun onExternalEditorUnavailable() {
        _events.trySend(ScriptEvent.Message("未找到可编辑文本文件的本地应用"))
    }

    fun retryContent() {
        val script = currentScript ?: return
        if (!_uiState.value.isLoadingContent) loadContent(script)
    }

    fun cancelEdit() {
        _uiState.update {
            it.copy(
                editContent = it.originalContent,
                isEditing = false,
                hasLocalDraftChanges = false
            )
        }
    }

    private suspend fun showInlineDraft(draft: ScriptDraft) {
        val contentResult = scriptRepo.readDraftText(draft, INLINE_EDITOR_MAX_BYTES)
        val content = contentResult.getOrNull()
        if (content == null) {
            showContentLoadFailure(contentResult.exceptionOrNull(), draft)
            return
        }
        _uiState.update {
            it.copy(
                editContent = content,
                originalContent = content,
                isEditing = false,
                isLoadingContent = false,
                contentLoadFailed = false,
                isContentReadOnly = false,
                contentWarning = null,
                hasUtf8Bom = draft.hasUtf8Bom,
                contentMode = ScriptContentMode.INLINE,
                draft = draft,
                previewPage = null
            )
        }
    }

    private suspend fun showPagedDraft(draft: ScriptDraft) {
        val pageResult = scriptRepo.readDraftPage(draft, 0)
        val page = pageResult.getOrNull()
        if (page == null) {
            showContentLoadFailure(pageResult.exceptionOrNull(), draft)
            return
        }
        val exceedsEditableLimit = draft.sizeBytes > EXTERNAL_EDITOR_MAX_BYTES
        _uiState.update {
            it.copy(
                editContent = page.content,
                originalContent = "",
                isEditing = false,
                isLoadingContent = false,
                contentLoadFailed = false,
                isContentReadOnly = exceedsEditableLimit,
                contentWarning = pagedDraftMessage(draft),
                hasUtf8Bom = draft.hasUtf8Bom,
                contentMode = ScriptContentMode.PAGED,
                draft = draft,
                previewPage = page
            )
        }
    }

    private fun showUnavailableDraft(draft: ScriptDraft) {
        _uiState.update {
            it.copy(
                isEditing = false,
                isLoadingContent = false,
                contentLoadFailed = false,
                isContentReadOnly = true,
                contentWarning = "文件不是有效的 UTF-8 文本。为避免乱码和误覆盖，仅保留原始文件下载。",
                hasUtf8Bom = draft.hasUtf8Bom,
                contentMode = ScriptContentMode.UNAVAILABLE,
                draft = draft,
                previewPage = null
            )
        }
    }

    private fun showContentLoadFailure(error: Throwable?, draft: ScriptDraft? = null) {
        _uiState.update {
            it.copy(
                isLoadingContent = false,
                contentLoadFailed = true,
                editContent = "",
                originalContent = "",
                draft = draft
            )
        }
        _events.trySend(ScriptEvent.Message(error?.message ?: "加载失败"))
    }

    private fun loadPreviewPage(pageIndex: Int) {
        val draft = _uiState.value.draft ?: return
        if (_uiState.value.isLoadingPreviewPage) return
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPreviewPage = true) }
            val result = scriptRepo.readDraftPage(draft, pageIndex)
            val page = result.getOrNull()
            _uiState.update {
                if (page == null) {
                    it.copy(isLoadingPreviewPage = false)
                } else {
                    it.copy(
                        previewPage = page,
                        editContent = page.content,
                        isLoadingPreviewPage = false
                    )
                }
            }
            if (page == null) {
                _events.trySend(ScriptEvent.Message(result.exceptionOrNull()?.message ?: "加载分段失败"))
            }
        }
    }

    private suspend fun uploadDraft(draft: ScriptDraft, force: Boolean) {
        val result = scriptRepo.uploadDraft(draft, force)
        when (result.getOrNull()) {
            ScriptDraftUploadResult.SAVED -> {
                scriptRepo.discardDraft(draft)
                currentScript = null
                _uiState.update { clearContentState(it) }
                _events.trySend(ScriptEvent.Message("已保存"))
                loadScripts()
            }
            ScriptDraftUploadResult.CONFLICT -> {
                _uiState.update {
                    it.copy(
                        draft = draft,
                        isSavingContent = false,
                        showOverwriteConfirm = true
                    )
                }
            }
            null -> {
                _uiState.update { it.copy(draft = draft, isSavingContent = false) }
                _events.trySend(ScriptEvent.Message(result.exceptionOrNull()?.message ?: "保存失败"))
            }
        }
    }

    private fun discardAndCloseContent() {
        contentJob?.cancel()
        pageJob?.cancel()
        contentJob = null
        pageJob = null
        currentScript = null
        val draft = _uiState.value.draft
        _uiState.update { clearContentState(it) }
        if (draft != null) {
            viewModelScope.launch { scriptRepo.discardDraft(draft) }
        }
    }

    private fun clearContentState(state: ScriptUiState): ScriptUiState = state.copy(
        editingFilename = "",
        editingPath = "",
        editContent = "",
        originalContent = "",
        isEditing = false,
        isLoadingContent = false,
        isSavingContent = false,
        contentLoadFailed = false,
        isContentReadOnly = false,
        contentWarning = null,
        hasUtf8Bom = false,
        showContent = false,
        contentMode = ScriptContentMode.INLINE,
        draft = null,
        previewPage = null,
        isLoadingPreviewPage = false,
        hasLocalDraftChanges = false,
        showOverwriteConfirm = false,
        showDiscardDraftConfirm = false
    )

    private fun pagedDraftMessage(draft: ScriptDraft): String =
        if (draft.sizeBytes > EXTERNAL_EDITOR_MAX_BYTES) {
            "文件超过 10 MB，当前仅按段预览和下载，不允许从客户端回传。"
        } else {
            "大文件已缓存到应用私有目录并按段预览。请选择“本地编辑”，返回后再上传修改。"
        }

    // ── 新建文件 ──

    fun showNewFileDialog(path: String = "") {
        _uiState.update {
            it.copy(showNewFileDialog = true, newFileName = "", newFilePath = path)
        }
    }

    fun dismissNewFileDialog() {
        _uiState.update { it.copy(showNewFileDialog = false, newFileName = "", newFilePath = "") }
    }

    fun onNewFileNameChanged(name: String) {
        _uiState.update { it.copy(newFileName = name) }
    }

    fun createNewFile() {
        val s = _uiState.value
        val name = s.newFileName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            scriptRepo.addScript(name, s.newFilePath, "")
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            showNewFileDialog = false,
                            newFileName = ""
                        )
                    }
                    _events.trySend(ScriptEvent.Message("已创建 $name"))
                    loadScripts()
                }
                .onFailure { e ->
                    _events.trySend(ScriptEvent.Message(e.message ?: "操作失败"))
                }
        }
    }

    fun importScripts(uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.isImportingScripts) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingScripts = true) }
            var imported = 0
            val failures = mutableListOf<String>()

            for (uri in uris) {
                val document = try {
                    Result.success(withContext(Dispatchers.IO) { readImportedScript(uri) })
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Result.failure(e)
                }
                val name = document.getOrNull()?.first ?: uri.lastPathSegment.orEmpty()
                document.fold(
                    onSuccess = { (filename, content) ->
                        scriptRepo.addScript(filename, "", content)
                            .onSuccess { imported++ }
                            .onFailure { failures += "$filename: ${it.message ?: "上传失败"}" }
                    },
                    onFailure = { failures += "$name: ${it.message ?: "读取失败"}" }
                )
            }

            _uiState.update {
                it.copy(isImportingScripts = false)
            }
            if (imported > 0) _events.trySend(ScriptEvent.Message("已导入 $imported 个脚本"))
            if (failures.isNotEmpty()) _events.trySend(ScriptEvent.Message(failures.joinToString("；")))
            if (imported > 0) loadScripts()
        }
    }

    private fun readImportedScript(uri: Uri): Pair<String, String> {
        val resolver = context.contentResolver
        val filename = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: "imported-script.txt"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_IMPORTED_SCRIPT_BYTES) { "文件超过 10 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("无法读取文件")
        val content = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        return filename to content
    }

    // ── 操作菜单 ──

    fun showActionMenu(script: ScriptFile) {
        _uiState.update { it.copy(selectedScript = script, showActionMenu = true) }
    }

    fun dismissActionMenu() {
        _uiState.update { it.copy(selectedScript = null, showActionMenu = false) }
    }

    // ── 删除 ──

    fun showDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = true, showActionMenu = false) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDelete() {
        val script = _uiState.value.selectedScript ?: return
        val name = script.title ?: return
        viewModelScope.launch {
            scriptRepo.deleteScript(
                filename = name,
                path = script.parent ?: "",
                isDir = script.isDirectory
            )
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            selectedScript = null,
                            showDeleteConfirm = false
                        )
                    }
                    _events.trySend(ScriptEvent.Message("已删除 $name"))
                    loadScripts()
                }
                .onFailure { e ->
                    _events.trySend(ScriptEvent.Message(e.message ?: "删除失败"))
                }
        }
    }

    // ── 订阅管理 ──

    fun openSubscriptionLog(subscription: SubscriptionInfo) {
        val id = subscription.id ?: return
        closeSubscriptionLog()
        _uiState.update { it.copy(subscriptionLog = SubscriptionLogUiState(subscription)) }
        subscriptionLogJob = viewModelScope.launch {
            val initial = subscriptionRepo.getSubscriptionLog(
                id = id,
                limit = SUBSCRIPTION_LOG_CHUNK_BYTES,
                tail = true
            )
            initial
                .onSuccess { chunk ->
                    _uiState.update { state ->
                        val current = state.subscriptionLog
                        if (current?.subscription?.id != id) state else state.copy(
                            subscriptionLog = current.copy(
                                content = chunk.content,
                                offset = chunk.offset,
                                nextOffset = chunk.nextOffset,
                                total = chunk.total,
                                truncated = chunk.truncated,
                                isLoading = false,
                                error = null
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        val current = state.subscriptionLog
                        if (current?.subscription?.id != id) state else state.copy(
                            subscriptionLog = current.copy(isLoading = false, error = error.message)
                        )
                    }
                }

            var keepPolling = subscription.isActiveSubscription()
            while (isActive && keepPolling && _uiState.value.subscriptionLog?.subscription?.id == id) {
                delay(SUBSCRIPTION_LOG_POLL_MS)
                subscriptionRepo.getSubscriptions().onSuccess { subscriptions ->
                    val latest = subscriptions.firstOrNull { it.id == id }
                    keepPolling = latest?.isActiveSubscription() == true
                    _uiState.update { state ->
                        val log = state.subscriptionLog
                        if (log?.subscription?.id != id) state else state.copy(
                            subscriptions = subscriptions.sortedWith(
                                compareBy<SubscriptionInfo> { item -> item.disabled }
                                    .thenByDescending { item -> item.id ?: 0 }
                            ),
                            subscriptionLog = log.copy(subscription = latest ?: log.subscription)
                        )
                    }
                }
                if (keepPolling) appendSubscriptionLog(id)
            }
        }
    }

    fun retrySubscriptionLog() {
        _uiState.value.subscriptionLog?.subscription?.let(::openSubscriptionLog)
    }

    fun loadOlderSubscriptionLog() {
        val current = _uiState.value.subscriptionLog ?: return
        val id = current.subscription.id ?: return
        if (!current.canLoadOlder || current.isLoadingOlder) return
        val start = (current.offset - SUBSCRIPTION_LOG_CHUNK_BYTES).coerceAtLeast(0)
        val limit = (current.offset - start).coerceAtMost(SUBSCRIPTION_LOG_CHUNK_BYTES.toLong()).toInt()
        olderSubscriptionLogJob?.cancel()
        olderSubscriptionLogJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(subscriptionLog = state.subscriptionLog?.copy(isLoadingOlder = true))
            }
            subscriptionRepo.getSubscriptionLog(id, offset = start, limit = limit, tail = false)
                .onSuccess { chunk ->
                    _uiState.update { state ->
                        val log = state.subscriptionLog
                        if (log?.subscription?.id != id) state else state.copy(
                            subscriptionLog = log.copy(
                                content = chunk.content + log.content,
                                offset = chunk.offset,
                                total = maxOf(log.total, chunk.total),
                                truncated = chunk.truncated,
                                isLoadingOlder = false,
                                error = null
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        val log = state.subscriptionLog
                        if (log?.subscription?.id != id) state else state.copy(
                            subscriptionLog = log.copy(isLoadingOlder = false, error = error.message)
                        )
                    }
                }
        }
    }

    fun closeSubscriptionLog() {
        subscriptionLogJob?.cancel()
        olderSubscriptionLogJob?.cancel()
        subscriptionLogJob = null
        olderSubscriptionLogJob = null
        _uiState.update { it.copy(subscriptionLog = null) }
    }

    private suspend fun appendSubscriptionLog(id: Int) {
        val current = _uiState.value.subscriptionLog?.takeIf { it.subscription.id == id } ?: return
        subscriptionRepo.getSubscriptionLog(
            id = id,
            offset = current.nextOffset,
            limit = SUBSCRIPTION_LOG_CHUNK_BYTES,
            tail = false
        ).onSuccess { chunk ->
            _uiState.update { state ->
                val log = state.subscriptionLog
                if (log?.subscription?.id != id) state else if (chunk.total < log.nextOffset) {
                    state.copy(
                        subscriptionLog = log.copy(
                            content = chunk.content,
                            offset = chunk.offset,
                            nextOffset = chunk.nextOffset,
                            total = chunk.total,
                            truncated = chunk.truncated
                        )
                    )
                } else {
                    state.copy(
                        subscriptionLog = log.copy(
                            content = log.content + chunk.content,
                            nextOffset = chunk.nextOffset,
                            total = chunk.total,
                            truncated = chunk.truncated,
                            error = null
                        )
                    )
                }
            }
        }.onFailure { error ->
            _uiState.update { state ->
                val log = state.subscriptionLog
                if (log?.subscription?.id != id) state else state.copy(
                    subscriptionLog = log.copy(error = error.message)
                )
            }
        }
    }

    fun loadSubscriptions(isRefresh: Boolean = false) {
        if (_uiState.value.isLoadingSubscriptions) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingSubscriptions = !it.hasLoadedSubscriptions,
                    isRefreshingSubscriptions = isRefresh
                )
            }
            subscriptionRepo.getSubscriptions()
                .onSuccess { subscriptions ->
                    _uiState.update {
                        it.copy(
                            subscriptions = subscriptions.sortedWith(
                                compareBy<SubscriptionInfo> { item -> item.disabled }
                                    .thenByDescending { item -> item.id ?: 0 }
                            ),
                            hasLoadedSubscriptions = true,
                            isLoadingSubscriptions = false,
                            isRefreshingSubscriptions = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            hasLoadedSubscriptions = true,
                            isLoadingSubscriptions = false,
                            isRefreshingSubscriptions = false
                        )
                    }
                    _events.trySend(ScriptEvent.Message(error.message ?: "加载失败"))
                }
        }
    }

    fun showNewSubscription() {
        _uiState.update {
            it.copy(
                showSubscriptionEditor = true,
                subscriptionDraft = SubscriptionDraft()
            )
        }
    }

    fun showEditSubscription(subscription: SubscriptionInfo) {
        _uiState.update {
            it.copy(
                showSubscriptionEditor = true,
                subscriptionDraft = subscription.toDraft()
            )
        }
    }

    fun dismissSubscriptionEditor() {
        if (_uiState.value.isSavingSubscription) return
        _uiState.update {
            it.copy(showSubscriptionEditor = false, subscriptionDraft = SubscriptionDraft())
        }
    }

    fun onSubscriptionDraftChanged(draft: SubscriptionDraft) {
        _uiState.update { it.copy(subscriptionDraft = draft) }
    }

    fun saveSubscription() {
        if (_uiState.value.isSavingSubscription) return
        val current = _uiState.value.subscriptionDraft
        val normalized = current.copy(
            name = current.name.trim(),
            url = current.url.trim(),
            schedule = current.schedule.trim(),
            branch = current.branch.trim(),
            alias = current.alias.trim().ifBlank {
                defaultSubscriptionAlias(current.url, current.branch, current.name)
            },
            whitelist = current.whitelist.trim(),
            blacklist = current.blacklist.trim(),
            dependences = current.dependences.trim(),
            extensions = current.extensions.trim(),
            subBefore = current.subBefore.trim(),
            proxy = current.proxy.trim(),
            privateKey = current.privateKey.trim(),
            username = current.username.trim(),
            password = current.password.trim(),
            intervalValue = current.intervalValue.coerceAtLeast(1)
        )
        val validationError = when {
            normalized.name.isBlank() -> "请输入订阅名称"
            normalized.url.isBlank() -> "请输入订阅链接"
            normalized.alias.isBlank() -> "无法生成订阅唯一值"
            normalized.scheduleType == "crontab" && normalized.schedule.isBlank() -> "请输入定时规则"
            normalized.type == "private-repo" && normalized.pullType == "ssh-key" &&
                normalized.privateKey.isBlank() -> "请输入私钥"
            normalized.type == "private-repo" && normalized.pullType == "user-pwd" &&
                normalized.username.isBlank() -> "请输入认证用户名"
            normalized.type == "private-repo" && normalized.pullType == "user-pwd" &&
                normalized.password.isBlank() -> "请输入密码或 Token"
            else -> null
        }
        if (validationError != null) {
            _events.trySend(ScriptEvent.Message(validationError))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSubscription = true, subscriptionDraft = normalized) }
            val result = if (normalized.id == null) {
                subscriptionRepo.addSubscription(normalized)
            } else {
                subscriptionRepo.updateSubscription(normalized)
            }
            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            showSubscriptionEditor = false,
                            subscriptionDraft = SubscriptionDraft(),
                            isSavingSubscription = false
                        )
                    }
                    _events.trySend(ScriptEvent.Message(if (normalized.id == null) "订阅已创建" else "订阅已更新"))
                    loadSubscriptions(isRefresh = true)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSavingSubscription = false) }
                    _events.trySend(ScriptEvent.Message(error.message ?: "操作失败"))
                }
        }
    }

    fun requestDeleteSubscription(subscription: SubscriptionInfo) {
        _uiState.update { it.copy(pendingDeleteSubscription = subscription) }
    }

    fun dismissDeleteSubscription() {
        _uiState.update { it.copy(pendingDeleteSubscription = null) }
    }

    fun confirmDeleteSubscription() {
        val subscription = _uiState.value.pendingDeleteSubscription ?: return
        val id = subscription.id ?: return
        performSubscriptionAction(id, "订阅已删除") {
            subscriptionRepo.deleteSubscription(id)
        }
        _uiState.update { it.copy(pendingDeleteSubscription = null) }
    }

    fun toggleSubscriptionEnabled(subscription: SubscriptionInfo) {
        val id = subscription.id ?: return
        val enable = subscription.disabled
        performSubscriptionAction(id, if (enable) "订阅已启用" else "订阅已禁用") {
            subscriptionRepo.setSubscriptionEnabled(id, enable)
        }
    }

    fun runOrStopSubscription(subscription: SubscriptionInfo) {
        val id = subscription.id ?: return
        val shouldStop = subscription.status == 0 || subscription.status == 3
        performSubscriptionAction(id, if (shouldStop) "停止指令已发送" else "订阅已加入运行队列") {
            if (shouldStop) subscriptionRepo.stopSubscription(id)
            else subscriptionRepo.runSubscription(id)
        }
    }

    private fun performSubscriptionAction(
        id: Int,
        successMessage: String,
        action: suspend () -> Result<Unit>
    ) {
        if (id in _uiState.value.busySubscriptionIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(busySubscriptionIds = it.busySubscriptionIds + id) }
            action()
                .onSuccess {
                    _uiState.update {
                        it.copy(busySubscriptionIds = it.busySubscriptionIds - id)
                    }
                    _events.trySend(ScriptEvent.Message(successMessage))
                    loadSubscriptions(isRefresh = true)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(busySubscriptionIds = it.busySubscriptionIds - id)
                    }
                    _events.trySend(ScriptEvent.Message(error.message ?: "操作失败"))
                }
        }
    }

    // ── 下载脚本到本地 ──

    fun prepareScriptDownload() {
        _uiState.update { it.copy(showActionMenu = false) }
    }

    fun cancelScriptDownloadSelection() {
        _uiState.update { it.copy(selectedScript = null, showActionMenu = false) }
    }

    fun clearDownloadedScript() {
        _uiState.update { it.copy(downloadedScript = null) }
    }

    fun downloadScript(destination: Uri) {
        val script = _uiState.value.selectedScript ?: return
        val name = script.title ?: return
        val dir = script.parent ?: ""   // 所在目录（相对脚本根目录）
        _uiState.update { it.copy(showActionMenu = false, isDownloadingScript = true) }

        viewModelScope.launch {
            scriptRepo.exportScript(name, dir, destination.toString())
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            selectedScript = null,
                            isDownloadingScript = false,
                            downloadedScript = SavedScriptDocument(destination.toString(), name)
                        )
                    }
                }
                .onFailure { e ->
                    runCatching { context.contentResolver.delete(destination, null, null) }
                    _uiState.update {
                        it.copy(selectedScript = null, isDownloadingScript = false)
                    }
                    _events.trySend(ScriptEvent.Message(e.message ?: "下载失败"))
                }
        }
    }
}

internal fun defaultSubscriptionAlias(url: String, branch: String, name: String): String {
    val withoutQuery = url.substringBefore('?').substringBefore('#').removeSuffix(".git").trimEnd('/')
    val schemeLess = withoutQuery.substringAfter("://", withoutQuery)
    val path = schemeLess.substringAfter('@', schemeLess)
    val segments = path.split('/', ':').filter(String::isNotBlank)
    val source = segments.takeLast(2).joinToString("_").ifBlank { name }
    val withBranch = listOf(source, branch.trim()).filter(String::isNotBlank).joinToString("_")
    return withBranch.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
}

private fun SubscriptionInfo.isActiveSubscription(): Boolean = status == 0 || status == 3
