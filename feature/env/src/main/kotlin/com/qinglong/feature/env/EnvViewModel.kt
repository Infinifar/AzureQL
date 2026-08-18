package com.qinglong.feature.env

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qinglong.core.domain.EnvRepository
import com.qinglong.core.model.EnvInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
private const val BACKUP_DIR = "environments"
private const val BACKUP_FILE = "envs_backup.json"
private val exportRegex = Regex("""export\s+(\w+)\s*=\s*["']([^"']*)["']""")

@HiltViewModel
class EnvViewModel @Inject constructor(
    private val envRepo: EnvRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnvUiState())
    val uiState: StateFlow<EnvUiState> = _uiState.asStateFlow()

    // 去重暂存
    private var pendingName = ""
    private var pendingValue = ""
    private var pendingRemarks = ""

    init { loadEnvs() }

    fun loadEnvs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoading = true) }
            envRepo.getEnvs(search = _uiState.value.searchQuery)
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(envs = list, isRefreshing = false, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, isLoading = false, error = e.message)
                    }
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

    fun batchEnableSelected() = batchEnable(_uiState.value.selectedIds.toList())
    fun batchDisableSelected() = batchDisable(_uiState.value.selectedIds.toList())
    fun batchDeleteSelected() = batchDelete(_uiState.value.selectedIds.toList())

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
            val result = existing?.id?.let { id ->
                envRepo.updateEnv(id, name, value, remarks)
            } ?: envRepo.addEnvs(listOf(Triple(name, value, remarks)))
            result
                .onSuccess {
                    _uiState.update { it.copy(editingEnv = null, showEditDialog = false) }
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
                val envs = _uiState.value.envs
                val dir = File(context.getExternalFilesDir(null), BACKUP_DIR)
                dir.mkdirs()
                val file = File(dir, BACKUP_FILE)
                withContext(Dispatchers.IO) {
                    file.writeText(json.encodeToString(envs))
                }
                _uiState.update { it.copy(successMessage = "已导出 ${envs.size} 条变量到 ${file.absolutePath}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "导出失败: ${e.message}") }
            }
        }
    }

    fun importEnvs() {
        viewModelScope.launch {
            try {
                val dir = File(context.getExternalFilesDir(null), BACKUP_DIR)
                val file = File(dir, BACKUP_FILE)
                if (!file.exists()) {
                    _uiState.update { it.copy(error = "备份文件不存在: ${file.absolutePath}") }
                    return@launch
                }
                val text = withContext(Dispatchers.IO) { file.readText() }
                val imported = json.decodeFromString<List<EnvInfo>>(text)
                if (imported.isEmpty()) {
                    _uiState.update { it.copy(error = "备份文件为空") }
                    return@launch
                }
                val requests = imported.mapNotNull {
                    val n = it.name ?: return@mapNotNull null
                    val v = it.value ?: return@mapNotNull null
                    Triple(n, v, it.remarks)
                }
                envRepo.addEnvs(requests)
                    .onSuccess {
                        _uiState.update { it.copy(successMessage = "已导入 ${requests.size} 条变量") }
                        loadEnvs()
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = "导入失败: ${e.message}") }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "导入失败: ${e.message}") }
            }
        }
    }
}
