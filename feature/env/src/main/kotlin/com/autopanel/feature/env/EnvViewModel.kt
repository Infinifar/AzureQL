package com.autopanel.feature.env

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.EnvRepository
import com.autopanel.core.model.EnvInfo
import com.autopanel.core.model.EnvStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
private const val BACKUP_DIR = "environments"
private const val BACKUP_FILE = "envs_backup.json"
private const val IMPORT_BATCH_SIZE = 25
private val exportRegex = Regex("""export\s+(\w+)\s*=\s*["']([^"']*)["']""")

/** 环境变量名称合法规则：以字母/下划线开头，后续为字母数字下划线 */
private val envNameRegex = Regex("^[a-zA-Z_][a-zA-Z0-9_]*\$")

@Serializable
private data class EnvBackupEntry(
    val name: String? = null,
    val value: String? = null,
    val remarks: String? = null,
    val status: Int? = null,
    val isPinned: Int? = null
)

private data class EnvBackupKey(val name: String, val value: String)

@HiltViewModel
class EnvViewModel @Inject constructor(
    private val envRepo: EnvRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnvUiState())
    val uiState: StateFlow<EnvUiState> = _uiState.asStateFlow()

    private var pendingName = ""
    private var pendingValue = ""
    private var pendingRemarks = ""

    init { loadEnvs() }

    fun loadEnvs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoading = true) }
            envRepo.getEnvs(search = _uiState.value.searchQuery)
                .onSuccess { list ->
                    _uiState.update { it.copy(envs = list, isRefreshing = false, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isRefreshing = false, isLoading = false, error = e.message) }
                }
        }
    }

    fun refresh() = loadEnvs()

    fun onSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadEnvs()
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }

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
            if (it.selectedIds.size == it.envs.size) it.copy(selectedIds = emptySet())
            else it.copy(selectedIds = it.envs.mapNotNull { e -> e.id }.toSet())
        }
    }

    fun batchEnable(ids: List<Int>) = batchOp(ids) { envRepo.enableEnvs(it) }
    fun batchDisable(ids: List<Int>) = batchOp(ids) { envRepo.disableEnvs(it) }
    fun batchDelete(ids: List<Int>) = batchOp(ids) { envRepo.deleteEnvs(it) }
    fun batchPin(ids: List<Int>) = batchOp(ids) { envRepo.pinEnvs(it) }
    fun batchUnpin(ids: List<Int>) = batchOp(ids) { envRepo.unpinEnvs(it) }

    fun batchEnableSelected() = batchEnable(_uiState.value.selectedIds.toList())
    fun batchDisableSelected() = batchDisable(_uiState.value.selectedIds.toList())
    fun batchPinSelected() = batchPin(_uiState.value.selectedIds.toList())
    fun batchUnpinSelected() = batchUnpin(_uiState.value.selectedIds.toList())

    /** 单个变量开关切换：绿=启用，红=禁用 */
    fun toggleStatus(env: EnvInfo) {
        val id = env.id ?: return
        val enable = env.status != EnvStatus.ENABLED
        // 乐观更新，开关立即翻转
        _uiState.update { s ->
            s.copy(envs = s.envs.map {
                if (it.id == id) it.copy(status = if (enable) EnvStatus.ENABLED else EnvStatus.DISABLED)
                else it
            })
        }
        viewModelScope.launch {
            val result = if (enable) envRepo.enableEnvs(listOf(id)) else envRepo.disableEnvs(listOf(id))
            result.onFailure { e ->
                _uiState.update { it.copy(error = "操作失败: ${e.message}") }
                loadEnvs() // 失败回滚到服务端真实状态
            }
        }
    }

    fun togglePin(env: EnvInfo) {
        val id = env.id ?: return
        val pin = !env.pinned
        updatePinnedState(setOf(id), pin)
        viewModelScope.launch {
            val result = if (pin) envRepo.pinEnvs(listOf(id)) else envRepo.unpinEnvs(listOf(id))
            result
                .onSuccess { loadEnvs() }
                .onFailure { error ->
                    updatePinnedState(setOf(id), !pin)
                    _uiState.update {
                        it.copy(error = if (pin) "置顶失败: ${error.message}" else "取消置顶失败: ${error.message}")
                    }
                }
        }
    }

    private fun updatePinnedState(ids: Set<Int>, pinned: Boolean) {
        _uiState.update { state ->
            state.copy(
                envs = state.envs
                    .map { env ->
                        if (env.id?.let(ids::contains) == true) {
                            env.copy(isPinned = if (pinned) 1 else 0)
                        } else {
                            env
                        }
                    }
                    .sortedByDescending(EnvInfo::pinned)
            )
        }
    }

    fun batchDeleteSelected() {
        if (_uiState.value.selectedIds.isEmpty()) return
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun confirmDeleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        _uiState.update { it.copy(showDeleteConfirm = false) }
        batchDelete(ids)
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    private fun batchOp(ids: List<Int>, op: suspend (List<Int>) -> Result<Unit>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            op(ids)
                .onFailure { e -> _uiState.update { it.copy(error = "操作失败: ${e.message}") } }
            _uiState.update { it.copy(isBatchMode = false, selectedIds = emptySet()) }
            loadEnvs()
        }
    }

    fun showEditDialog(env: EnvInfo? = null) {
        _uiState.update { it.copy(editingEnv = env, showEditDialog = true) }
    }

    fun dismissEditDialog() {
        _uiState.update { it.copy(editingEnv = null, showEditDialog = false) }
    }

    fun submitEdit(name: String, value: String, remarks: String?) {
        val existing = _uiState.value.editingEnv
        if (existing == null) {
            val dup = _uiState.value.envs.find { it.name == name }
            if (dup != null) {
                pendingName = name
                pendingValue = value
                pendingRemarks = remarks ?: ""
                _uiState.update { it.copy(duplicateEnv = dup, showDuplicateDialog = true) }
                return
            }
        }
        doSubmitEdit(name, value, remarks)
    }

    fun confirmDuplicate() {
        _uiState.update { it.copy(duplicateEnv = null, showDuplicateDialog = false) }
        doSubmitEdit(pendingName, pendingValue, pendingRemarks)
    }

    fun dismissDuplicate() {
        _uiState.update { it.copy(duplicateEnv = null, showDuplicateDialog = false) }
    }

    private fun doSubmitEdit(name: String, value: String, remarks: String?) {
        val existing = _uiState.value.editingEnv
        viewModelScope.launch {
            val result: Result<Unit> = existing?.id?.let { id ->
                envRepo.updateEnv(id, name, value, remarks)
            } ?: envRepo.addEnvs(listOf(Triple(name, value, remarks))).map { Unit }
            result
                .onSuccess {
                    _uiState.update { it.copy(editingEnv = null, showEditDialog = false, successMessage = "保存成功") }
                    loadEnvs()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun showImportDialog() {
        _uiState.update { it.copy(showImportDialog = true, importText = "") }
    }

    fun dismissImportDialog() {
        _uiState.update { it.copy(showImportDialog = false, importText = "") }
    }

    fun onImportTextChanged(text: String) {
        _uiState.update { it.copy(importText = text) }
    }

    fun parseAndImport() {
        val text = _uiState.value.importText.trim()
        if (text.isEmpty()) return

        val parsed = exportRegex.findAll(text).map { mr ->
            Triple(mr.groupValues[1], mr.groupValues[2], null as String?)
        }.toList()

        if (parsed.isEmpty()) {
            _uiState.update { it.copy(error = "未找到有效的 export 语句") }
            return
        }

        viewModelScope.launch {
            envRepo.addEnvs(parsed)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            showImportDialog = false,
                            importText = "",
                            successMessage = "已导入 ${parsed.size} 条变量"
                        )
                    }
                    loadEnvs()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "导入失败: ${e.message}") }
                }
        }
    }

    fun exportEnvs() {
        viewModelScope.launch {
            try {
                val envs = envRepo.getEnvs("").getOrElse { throw it }
                val backup = envs.map { env ->
                    EnvBackupEntry(
                        name = env.name,
                        value = env.value,
                        remarks = env.remarks,
                        status = env.status,
                        isPinned = env.isPinned
                    )
                }
                val dir = File(context.getExternalFilesDir(null), BACKUP_DIR)
                dir.mkdirs()
                val file = File(dir, BACKUP_FILE)
                withContext(Dispatchers.IO) {
                    file.writeText(json.encodeToString(backup))
                }
                _uiState.update { it.copy(successMessage = "已导出 ${envs.size} 条变量到 ${file.absolutePath}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "导出失败: ${e.message}") }
            }
        }
    }

    fun importEnvs() {
        if (_uiState.value.isImportingBackup) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingBackup = true) }
            try {
                val dir = File(context.getExternalFilesDir(null), BACKUP_DIR)
                val file = File(dir, BACKUP_FILE)
                if (!file.exists()) {
                    _uiState.update { it.copy(error = "备份文件不存在: ${file.absolutePath}") }
                    return@launch
                }
                val text = withContext(Dispatchers.IO) { file.readText() }
                val imported = json.decodeFromString<List<EnvBackupEntry>>(text)
                if (imported.isEmpty()) {
                    _uiState.update { it.copy(error = "备份文件为空") }
                    return@launch
                }

                val validEntries = imported.mapNotNull { entry ->
                    val name = entry.name?.takeIf(envNameRegex::matches) ?: return@mapNotNull null
                    val value = entry.value ?: return@mapNotNull null
                    entry.copy(name = name, value = value)
                }.distinctBy { EnvBackupKey(it.name.orEmpty(), it.value.orEmpty()) }
                val invalidCount = imported.size - validEntries.size
                if (validEntries.isEmpty()) {
                    _uiState.update { it.copy(error = "备份中没有有效环境变量") }
                    return@launch
                }

                val before = envRepo.getEnvs("").getOrElse { throw it }
                val beforeKeys = before.mapNotNull(EnvInfo::backupKey).toSet()
                val candidates = validEntries.filter { it.backupKey() !in beforeKeys }

                candidates.chunked(IMPORT_BATCH_SIZE).forEach { batch ->
                    val request = batch.map { Triple(it.name.orEmpty(), it.value.orEmpty(), it.remarks) }
                    val batchResult = envRepo.addEnvs(request)
                    if (batchResult.isFailure) {
                        val currentKeys = envRepo.getEnvs("")
                            .getOrDefault(emptyList())
                            .mapNotNull(EnvInfo::backupKey)
                            .toSet()
                        batch
                            .filter { it.backupKey() !in currentKeys }
                            .forEach { entry ->
                                envRepo.addEnvs(
                                    listOf(Triple(entry.name.orEmpty(), entry.value.orEmpty(), entry.remarks))
                                )
                            }
                    }
                }

                val after = envRepo.getEnvs("").getOrElse { throw it }
                val afterByKey = after.mapNotNull { env -> env.backupKey()?.let { it to env } }.toMap()
                val successful = candidates.count(afterByKey::containsKey)
                val skipped = validEntries.size - candidates.size
                val failed = candidates.size - successful

                val pinIds = validEntries
                    .filter { it.isPinned == 1 }
                    .mapNotNull { afterByKey[it.backupKey()]?.id }
                val disableIds = validEntries
                    .filter { it.status == EnvStatus.DISABLED }
                    .mapNotNull { afterByKey[it.backupKey()]?.id }
                if (pinIds.isNotEmpty()) envRepo.pinEnvs(pinIds)
                if (disableIds.isNotEmpty()) envRepo.disableEnvs(disableIds)

                _uiState.update {
                    it.copy(
                        successMessage = buildString {
                            append("环境变量导入完成：新增 ").append(successful)
                            append("，跳过重复 ").append(skipped)
                            if (invalidCount > 0) append("，无效 ").append(invalidCount)
                            if (failed > 0) append("，失败 ").append(failed)
                        }
                    )
                }
                loadEnvs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "导入失败: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isImportingBackup = false) }
            }
        }
    }

    private fun EnvBackupEntry.backupKey(): EnvBackupKey =
        EnvBackupKey(name.orEmpty(), value.orEmpty())

    private fun EnvInfo.backupKey(): EnvBackupKey? {
        val validName = name ?: return null
        val validValue = value ?: return null
        return EnvBackupKey(validName, validValue)
    }
}
