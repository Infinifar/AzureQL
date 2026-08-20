package com.autopanel.feature.task

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.TaskInfo
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
import java.io.InputStream
import javax.inject.Inject

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
private const val BACKUP_DIR = "tasks"
private const val BACKUP_FILE = "tasks_backup.json"

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    private var pendingName = ""
    private var pendingCommand = ""
    private var pendingSchedule = ""

    init { loadTasks() }

    fun loadTasks(page: Int = 1) {
        viewModelScope.launch {
            _uiState.update {
                if (page == 1) it.copy(isRefreshing = true, isLoading = true)
                else it.copy(isLoadingMore = true)
            }
            taskRepo.getTasks(search = _uiState.value.searchQuery, page = page, size = 50)
                .onSuccess { (list, _) ->
                    _uiState.update {
                        it.copy(
                            tasks = if (page == 1) list else it.tasks + list,
                            currentPage = page,
                            hasMore = list.size >= 50,
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
                            isLoadingMore = false,
                            error = e.message
                        )
                    }
                }
        }
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
                    _uiState.update {
                        it.copy(error = if (pin) "置顶失败: ${error.message}" else "取消置顶失败: ${error.message}")
                    }
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
                .onFailure { e -> _uiState.update { it.copy(error = "操作失败: ${e.message}") } }
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

    fun submitEdit(name: String, command: String, schedule: String) {
        val existing = _uiState.value.editingTask
        if (existing == null) {
            val dup = _uiState.value.tasks.find { it.name == name && it.command == command }
            if (dup != null) {
                pendingName = name
                pendingCommand = command
                pendingSchedule = schedule
                _uiState.update { it.copy(duplicateTask = dup, showDuplicateDialog = true) }
                return
            }
        }
        doSubmitEdit(name, command, schedule)
    }

    fun confirmDuplicate() {
        _uiState.update { it.copy(duplicateTask = null, showDuplicateDialog = false) }
        doSubmitEdit(pendingName, pendingCommand, pendingSchedule)
    }

    fun dismissDuplicate() {
        _uiState.update { it.copy(duplicateTask = null, showDuplicateDialog = false) }
    }

    private fun doSubmitEdit(name: String, command: String, schedule: String) {
        val existing = _uiState.value.editingTask
        viewModelScope.launch {
            val result = existing?.id?.let { id ->
                taskRepo.updateTask(id, name, command, schedule)
            } ?: taskRepo.addTask(name, command, schedule)
            result
                .onSuccess {
                    _uiState.update { it.copy(editingTask = null, showEditDialog = false) }
                    loadTasks(1)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
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

    fun exportTasks() {
        viewModelScope.launch {
            try {
                val tasks = _uiState.value.tasks
                val dir = File(context.getExternalFilesDir(null), BACKUP_DIR)
                dir.mkdirs()
                val file = File(dir, BACKUP_FILE)
                withContext(Dispatchers.IO) {
                    file.writeText(json.encodeToString(tasks))
                }
                _uiState.update { it.copy(successMessage = "已导出 ${tasks.size} 条任务到 ${file.absolutePath}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "导出失败: ${e.message}") }
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
                        _uiState.update { it.copy(error = "备份文件不存在: ${file.absolutePath}") }
                        return@launch
                    }
                    withContext(Dispatchers.IO) { file.readText() }
                }
                val imported = json.decodeFromString<List<TaskInfo>>(text)
                if (imported.isEmpty()) {
                    _uiState.update { it.copy(error = "备份文件为空") }
                    return@launch
                }
                var success = 0
                for (task in imported) {
                    val name = task.name ?: continue
                    val cmd = task.command ?: continue
                    val sched = task.schedule ?: continue
                    taskRepo.addTask(name, cmd, sched)
                        .onSuccess { success++ }
                }
                _uiState.update { it.copy(successMessage = "已导入 $success / ${imported.size} 条任务") }
                loadTasks(1)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "导入失败: ${e.message}") }
            }
        }
    }
}
