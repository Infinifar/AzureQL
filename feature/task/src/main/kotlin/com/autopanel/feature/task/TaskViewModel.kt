package com.autopanel.feature.task

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.TaskDraft
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.model.TaskScheduleType
import com.autopanel.core.model.toDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
private const val BACKUP_DIR = "tasks"
private const val BACKUP_FILE = "tasks_backup.json"

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    private val _events = Channel<TaskEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pendingDraft: TaskDraft? = null
    private var refreshJob: Job? = null

    init { loadTasks() }

    fun loadTasks(page: Int = 1) {
        if (page == 1) refreshJob?.cancel()
        val job = viewModelScope.launch {
            val search = _uiState.value.searchQuery
            val labels = _uiState.value.selectedLabels
            _uiState.update {
                if (page == 1) it.copy(isRefreshing = true, isLoading = true)
                else it.copy(isLoadingMore = true)
            }
            if (page == 1) {
                taskRepo.getCachedTasks(search = search, page = page, size = PAGE_SIZE, labels = labels)
                    ?.let { (list, total) ->
                        _uiState.update {
                            it.copy(
                                tasks = list,
                                availableLabels = mergeLabels(it.availableLabels, list),
                                currentPage = page,
                                hasMore = hasMoreTasks(page, list.size, total),
                                isLoading = false
                            )
                        }
                    }
            }
            taskRepo.getTasks(search = search, page = page, size = PAGE_SIZE, labels = labels)
                .onSuccess { (list, total) ->
                    _uiState.update {
                        it.copy(
                            tasks = if (page == 1) list else it.tasks + list,
                            availableLabels = mergeLabels(it.availableLabels, list),
                            currentPage = page,
                            hasMore = hasMoreTasks(page, list.size, total),
                            isRefreshing = false,
                            isLoading = false,
                            isLoadingMore = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isLoading = false,
                            isLoadingMore = false
                        )
                    }
                    _events.trySend(TaskEvent.Message(e.message ?: "加载失败"))
                }
        }
        if (page == 1) refreshJob = job
    }

    fun loadMore() {
        val s = _uiState.value
        if (!s.isLoadingMore && s.hasMore) loadTasks(s.currentPage + 1)
    }

    fun refresh() = loadTasks(1)

    fun onSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadTasks(1)
    }

    fun toggleLabelFilter(label: String) {
        _uiState.update { state ->
            val selected = state.selectedLabels.toMutableSet().apply {
                if (!add(label)) remove(label)
            }
            state.copy(
                selectedLabels = selected,
                isBatchMode = false,
                selectedIds = emptySet()
            )
        }
        loadTasks(1)
    }

    fun clearLabelFilters() {
        if (_uiState.value.selectedLabels.isEmpty()) return
        _uiState.update {
            it.copy(selectedLabels = emptySet(), isBatchMode = false, selectedIds = emptySet())
        }
        loadTasks(1)
    }

    fun toggleBatchMode() {
        _uiState.update {
            if (it.isBatchMode) it.copy(isBatchMode = false, selectedIds = emptySet())
            else it.copy(isBatchMode = true, selectedIds = emptySet())
        }
    }

    fun toggleSelection(id: Int) {
        _uiState.update {
            val new = it.selectedIds.toMutableSet()
            if (new.contains(id)) new.remove(id) else new.add(id)
            it.copy(selectedIds = new)
        }
    }

    fun selectAll() {
        _uiState.update {
            if (it.selectedIds.size == it.tasks.size) it.copy(selectedIds = emptySet())
            else it.copy(selectedIds = it.tasks.mapNotNull { t -> t.id }.toSet())
        }
    }

    fun runTask(task: TaskInfo) {
        task.id?.let { batchRun(listOf(it)) }
    }

    fun stopTask(task: TaskInfo) {
        task.id?.let { batchStop(listOf(it)) }
    }

    fun batchRun(ids: List<Int>) = batchOp(ids) { taskRepo.runTasks(it) }
    fun batchStop(ids: List<Int>) = batchOp(ids) { taskRepo.stopTasks(it) }
    fun batchEnable(ids: List<Int>) = batchOp(ids) { taskRepo.enableTasks(it) }
    fun batchDisable(ids: List<Int>) = batchOp(ids) { taskRepo.disableTasks(it) }
    fun batchPin(ids: List<Int>) = batchOp(ids) { taskRepo.pinTasks(it) }
    fun batchUnpin(ids: List<Int>) = batchOp(ids) { taskRepo.unpinTasks(it) }
    fun batchDelete(ids: List<Int>) = batchOp(ids) { taskRepo.deleteTasks(it) }

    fun togglePin(task: TaskInfo) {
        val id = task.id ?: return
        val pin = !task.pinned
        updatePinnedState(setOf(id), pin)
        viewModelScope.launch {
            val result = if (pin) taskRepo.pinTasks(listOf(id)) else taskRepo.unpinTasks(listOf(id))
            result
                .onSuccess { loadTasks(1) }
                .onFailure { error ->
                    updatePinnedState(setOf(id), !pin)
                    _events.trySend(
                        TaskEvent.Message(
                            if (pin) "置顶失败: ${error.message}" else "取消置顶失败: ${error.message}"
                        )
                    )
                }
        }
    }

    private fun updatePinnedState(ids: Set<Int>, pinned: Boolean) {
        _uiState.update { state ->
            state.copy(
                tasks = state.tasks
                    .map { task ->
                        if (task.id?.let(ids::contains) == true) {
                            task.copy(isPinned = if (pinned) 1 else 0)
                        } else {
                            task
                        }
                    }
                    .sortedByDescending(TaskInfo::pinned)
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 50

        fun hasMoreTasks(page: Int, pageItemCount: Int, total: Int): Boolean =
            if (total > 0) page * PAGE_SIZE < total else pageItemCount >= PAGE_SIZE

        fun mergeLabels(existing: List<String>, tasks: List<TaskInfo>): List<String> =
            (existing + tasks.flatMap { it.labels.orEmpty() })
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun batchRunSelected() = batchRun(_uiState.value.selectedIds.toList())
    fun batchStopSelected() = batchStop(_uiState.value.selectedIds.toList())
    fun batchEnableSelected() = batchEnable(_uiState.value.selectedIds.toList())
    fun batchDisableSelected() = batchDisable(_uiState.value.selectedIds.toList())
    fun batchPinSelected() = batchPin(_uiState.value.selectedIds.toList())
    fun batchUnpinSelected() = batchUnpin(_uiState.value.selectedIds.toList())
    fun batchDeleteSelected() = batchDelete(_uiState.value.selectedIds.toList())

    private fun batchOp(ids: List<Int>, op: suspend (List<Int>) -> Result<Unit>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            op(ids)
                .onFailure { e -> _events.trySend(TaskEvent.Message("操作失败: ${e.message}")) }
            _uiState.update { it.copy(isBatchMode = false, selectedIds = emptySet()) }
            loadTasks(1)
        }
    }

    fun showEditDialog(task: TaskInfo? = null) {
        _uiState.update { it.copy(editingTask = task, showEditDialog = true) }
    }

    fun dismissEditDialog() {
        _uiState.update { it.copy(editingTask = null, showEditDialog = false) }
    }

    fun submitEdit(draft: TaskDraft) {
        val normalized = draft.copy(
            name = draft.name.trim(),
            command = draft.command.trim(),
            schedule = draft.schedule.trim(),
            extraSchedules = draft.extraSchedules.map(String::trim),
            labels = draft.labels.map(String::trim).filter(String::isNotBlank).distinct(),
            logName = draft.logName.trim(),
            workDir = draft.workDir.trim(),
            taskBefore = draft.taskBefore.trim(),
            taskAfter = draft.taskAfter.trim()
        )
        validateTaskDraft(normalized)?.let { message ->
            _events.trySend(TaskEvent.Message(message))
            return
        }

        if (normalized.id == null) {
            val dup = _uiState.value.tasks.find {
                it.name == normalized.name && it.command == normalized.command
            }
            if (dup != null) {
                pendingDraft = normalized
                _uiState.update { it.copy(duplicateTask = dup, showDuplicateDialog = true) }
                return
            }
        }
        doSubmitEdit(normalized)
    }

    fun confirmDuplicate() {
        val draft = pendingDraft ?: return
        pendingDraft = null
        _uiState.update { it.copy(duplicateTask = null, showDuplicateDialog = false) }
        doSubmitEdit(draft)
    }

    fun dismissDuplicate() {
        pendingDraft = null
        _uiState.update { it.copy(duplicateTask = null, showDuplicateDialog = false) }
    }

    private fun doSubmitEdit(draft: TaskDraft) {
        viewModelScope.launch {
            val result = if (draft.id == null) taskRepo.addTask(draft) else taskRepo.updateTask(draft)
            result
                .onSuccess {
                    _uiState.update { it.copy(editingTask = null, showEditDialog = false) }
                    loadTasks(1)
                }
                .onFailure { e ->
                    _events.trySend(TaskEvent.Message(e.message ?: "保存失败"))
                }
        }
    }

    fun showLog(task: TaskInfo) {
        task.id?.let { id ->
            viewModelScope.launch {
                taskRepo.getTaskLog(id)
                    .onSuccess { log ->
                        _uiState.update { it.copy(logContent = log, showLogSheet = true) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(logContent = "加载失败: ${e.message}", showLogSheet = true) }
                    }
            }
        }
    }

    fun dismissLog() {
        _uiState.update { it.copy(logContent = null, showLogSheet = false) }
    }

    fun exportTasks(uri: Uri? = null) {
        viewModelScope.launch {
            try {
                val tasks = _uiState.value.tasks
                val jsonText = json.encodeToString(tasks)
                if (uri != null) {
                    withContext(Dispatchers.IO) {
                        val out = context.contentResolver.openOutputStream(uri, "rwt")
                            ?: context.contentResolver.openOutputStream(uri, "w")
                            ?: throw IOException("无法写入所选位置")
                        out.use { it.write(jsonText.toByteArray(Charsets.UTF_8)) }
                    }
                    _events.trySend(TaskEvent.Message("已导出 ${tasks.size} 条任务"))
                } else {
                    val dir = File(context.getExternalFilesDir(null), BACKUP_DIR)
                    dir.mkdirs()
                    val file = File(dir, BACKUP_FILE)
                    withContext(Dispatchers.IO) {
                        file.writeText(jsonText)
                    }
                    _events.trySend(TaskEvent.Message("已导出 ${tasks.size} 条任务到 ${file.absolutePath}"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend(TaskEvent.Message("导出失败: ${e.message}"))
            }
        }
    }

    fun importTasks(source: InputStream? = null) {
        viewModelScope.launch {
            try {
                val text = if (source != null) {
                    source.use { input ->
                        withContext(Dispatchers.IO) {
                            input.bufferedReader().use { it.readText() }
                        }
                    }
                } else {
                    val dir = File(context.getExternalFilesDir(null), BACKUP_DIR)
                    val file = File(dir, BACKUP_FILE)
                    if (!file.exists()) {
                        _events.trySend(TaskEvent.Message("备份文件不存在: ${file.absolutePath}"))
                        return@launch
                    }
                    withContext(Dispatchers.IO) { file.readText() }
                }
                val imported = json.decodeFromString<List<TaskInfo>>(text)
                if (imported.isEmpty()) {
                    _events.trySend(TaskEvent.Message("备份文件为空"))
                    return@launch
                }
                var success = 0
                for (task in imported) {
                    if (task.name.isNullOrBlank() || task.command.isNullOrBlank() || task.schedule.isNullOrBlank()) {
                        continue
                    }
                    taskRepo.addTask(task.toDraft().copy(id = null))
                        .onSuccess { success++ }
                }
                _events.trySend(TaskEvent.Message("已导入 $success / ${imported.size} 条任务"))
                loadTasks(1)
            } catch (e: Exception) {
                _events.trySend(TaskEvent.Message("导入失败: ${e.message}"))
            }
        }
    }
}

internal fun containsTaskCommand(command: String): Boolean =
    Regex("(^|[;&|\\r\\n])\\s*task(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(command)

private fun validateTaskDraft(draft: TaskDraft): String? = when {
    draft.name.isBlank() -> "请输入任务名称"
    draft.command.isBlank() -> "请输入任务命令"
    draft.scheduleType == TaskScheduleType.NORMAL && draft.schedule.isBlank() -> "请输入定时规则"
    draft.scheduleType == TaskScheduleType.NORMAL && draft.extraSchedules.any(String::isBlank) ->
        "请填写或删除空的附加定时规则"
    draft.logName.length > 100 -> "日志名称不能超过 100 个字符"
    containsTaskCommand(draft.taskBefore) -> "执行前不能包含 task 命令"
    containsTaskCommand(draft.taskAfter) -> "执行后不能包含 task 命令"
    else -> null
}
