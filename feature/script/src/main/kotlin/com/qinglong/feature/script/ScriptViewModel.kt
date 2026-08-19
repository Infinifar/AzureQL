package com.qinglong.feature.script

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qinglong.core.domain.ScriptRepository
import com.qinglong.core.model.ScriptFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ScriptViewModel @Inject constructor(
    private val scriptRepo: ScriptRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScriptUiState())
    val uiState: StateFlow<ScriptUiState> = _uiState.asStateFlow()

    init { loadScripts() }

    // ── 列表加载 ──

    fun loadScripts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoading = true) }
            scriptRepo.getScripts()
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            scripts = sortScripts(list),
                            isRefreshing = false,
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, isLoading = false, error = e.message)
                    }
                }
        }
    }

    fun refresh() = loadScripts()
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }

    // ── 目录优先排序 ──

    private fun sortScripts(list: List<ScriptFile>): List<ScriptFile> {
        return list.sortedWith(compareByDescending<ScriptFile> { it.isDirectory }.thenBy { it.title })
            .map { file ->
                val children = file.children
                if (children != null) {
                    file.copy(children = sortScripts(children))
                } else file
            }
    }

    // ── 查看/编辑内容 ──

    fun loadContent(filename: String, path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    editingFilename = filename,
                    editingPath = path,
                    isLoadingContent = true,
                    showContent = true
                )
            }
            scriptRepo.getScriptContent(filename, path)
                .onSuccess { content ->
                    _uiState.update {
                        it.copy(
                            editContent = content,
                            originalContent = content,
                            isEditing = false,
                            isLoadingContent = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoadingContent = false, error = e.message)
                    }
                }
        }
    }

    fun closeContent() {
        _uiState.update { it.copy(showContent = false, isEditing = false) }
    }

    fun enterEditMode() {
        _uiState.update { it.copy(isEditing = true) }
    }

    fun onContentChanged(content: String) {
        _uiState.update { it.copy(editContent = content) }
    }

    fun saveContent() {
        val s = _uiState.value
        viewModelScope.launch {
            scriptRepo.updateScript(s.editingFilename, s.editingPath, s.editContent)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            originalContent = s.editContent,
                            isEditing = false,
                            successMessage = "已保存"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun cancelEdit() {
        _uiState.update {
            it.copy(editContent = it.originalContent, isEditing = false)
        }
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
                            newFileName = "",
                            successMessage = "已创建 $name"
                        )
                    }
                    loadScripts()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
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
                            showDeleteConfirm = false,
                            successMessage = "已删除 $name"
                        )
                    }
                    loadScripts()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ── 下载脚本到本地 ──

    fun downloadScript() {
        val script = _uiState.value.selectedScript ?: return
        val name = script.title ?: return
        val dir = script.parent ?: ""   // 所在目录（相对脚本根目录）
        _uiState.update { it.copy(showActionMenu = false) }

        viewModelScope.launch {
            scriptRepo.getScriptContent(name, dir)
                .onSuccess { content ->
                    try {
                        val base = File(context.getExternalFilesDir(null), "scripts")
                        base.mkdirs()
                        val localPath = if (dir.isNotEmpty()) "$dir/$name" else name
                        val file = File(base, localPath)
                        file.parentFile?.mkdirs()
                        withContext(Dispatchers.IO) {
                            file.writeText(content)
                        }
                        _uiState.update {
                            it.copy(successMessage = "已下载到 ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = "下载失败: ${e.message}") }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }
}
