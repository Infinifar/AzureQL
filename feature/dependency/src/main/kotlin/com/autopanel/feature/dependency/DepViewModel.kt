package com.autopanel.feature.dependency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.DependencyRepository
import com.autopanel.core.model.DependencyInfo
import com.autopanel.core.model.boundedUtf8Tail
import com.autopanel.core.model.DependencyStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DepViewModel @Inject constructor(
    private val depRepo: DependencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DepUiState())
    val uiState: StateFlow<DepUiState> = _uiState.asStateFlow()

    private val _events = Channel<DepEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var statusPollJob: Job? = null

    init { loadDeps() }

    fun loadDeps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoading = true) }
            val s = _uiState.value
            depRepo.getDependencies(search = s.searchQuery, type = s.typeFilter)
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(deps = list, isRefreshing = false, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, isLoading = false)
                    }
                    _events.trySend(DepEvent.Message(e.message ?: "加载失败"))
                }
        }
    }

    fun refresh() = loadDeps()

    fun onSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadDeps()
    }

    fun setTypeFilter(type: String) {
        _uiState.update { it.copy(typeFilter = if (it.typeFilter == type) "" else type) }
        loadDeps()
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
            if (it.selectedIds.size == it.deps.size) it.copy(selectedIds = emptySet())
            else it.copy(selectedIds = it.deps.mapNotNull { d -> d.id }.toSet())
        }
    }

    fun batchDelete(ids: List<Int>) {
        val actionableIds = ids.filterNot(::isDependencyOperationActive)
        if (actionableIds.isEmpty()) return
        viewModelScope.launch {
            depRepo.deleteDependencies(actionableIds)
                .onSuccess { returned ->
                    mergeDependencies(
                        returned.map { dependency ->
                            if (dependency.id?.let(actionableIds::contains) == true) {
                                dependency.copy(status = DependencyStatus.UNINSTALLING)
                            } else {
                                dependency
                            }
                        }
                    )
                    _uiState.update {
                        it.copy(
                            isBatchMode = false,
                            selectedIds = emptySet()
                        )
                    }
                    _events.trySend(DepEvent.Message("删除任务已提交"))
                    startStatusPolling(actionableIds.toSet())
                }
                .onFailure { e -> _events.trySend(DepEvent.Message("删除失败: ${e.message}")) }
        }
    }

    fun batchDeleteSelected() = batchDelete(_uiState.value.selectedIds.toList())

    fun batchReinstall(ids: List<Int>) {
        val actionableIds = ids.filterNot(::isDependencyOperationActive)
        if (actionableIds.isEmpty()) return
        viewModelScope.launch {
            depRepo.reinstallDependencies(actionableIds)
                .onSuccess { returned ->
                    mergeDependencies(returned)
                    _uiState.update {
                        it.copy(isBatchMode = false, selectedIds = emptySet())
                    }
                    _events.trySend(DepEvent.Message("重新安装已提交"))
                    startStatusPolling(actionableIds.toSet())
                }
                .onFailure { e -> _events.trySend(DepEvent.Message("重新安装失败: ${e.message}")) }
        }
    }

    fun batchReinstallSelected() = batchReinstall(_uiState.value.selectedIds.toList())

    fun requestReinstall(dep: DependencyInfo) {
        if (dep.id == null || dep.isOperationActive() || _uiState.value.isMutating) return
        _uiState.update { it.copy(confirmReinstall = dep) }
    }

    fun dismissReinstall() {
        _uiState.update { it.copy(confirmReinstall = null) }
    }

    fun confirmReinstall() {
        val dep = _uiState.value.confirmReinstall ?: return
        val id = dep.id ?: return
        _uiState.update { it.copy(confirmReinstall = null, isMutating = true) }
        viewModelScope.launch {
            depRepo.reinstallDependencies(listOf(id))
                .onSuccess { returned ->
                    mergeDependencies(returned)
                    _events.trySend(DepEvent.Message("${dep.name ?: "依赖"}重装任务已提交"))
                    startStatusPolling(setOf(id))
                }
                .onFailure { error ->
                    _events.trySend(DepEvent.Message("重新安装失败: ${error.message}"))
            }
            _uiState.update { it.copy(isMutating = false) }
        }
    }

    fun requestDelete(dep: DependencyInfo) {
        if (dep.id == null || dep.isOperationActive() || _uiState.value.isMutating) return
        _uiState.update { it.copy(confirmDelete = dep) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(confirmDelete = null) }
    }

    fun confirmDelete() {
        val dep = _uiState.value.confirmDelete ?: return
        val id = dep.id ?: return
        _uiState.update { it.copy(confirmDelete = null, isMutating = true) }
        viewModelScope.launch {
            depRepo.deleteDependencies(listOf(id))
                .onSuccess { returned ->
                    mergeDependencies(
                        returned.map { dependency ->
                            if (dependency.id == id) {
                                dependency.copy(status = DependencyStatus.UNINSTALLING)
                            } else {
                                dependency
                            }
                        }
                    )
                    _events.trySend(DepEvent.Message("${dep.name ?: "依赖"}删除任务已提交"))
                    startStatusPolling(setOf(id))
                }
                .onFailure { error ->
                    _events.trySend(DepEvent.Message("删除失败: ${error.message}"))
            }
            _uiState.update { it.copy(isMutating = false) }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editName = "", editType = "nodejs") }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun onEditNameChanged(name: String) {
        _uiState.update { it.copy(editName = name) }
    }

    fun onEditTypeChanged(type: String) {
        _uiState.update { it.copy(editType = type) }
    }

    fun addDependency() {
        val s = _uiState.value
        val name = s.editName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            depRepo.addDependency(name, s.editType)
                .onSuccess { returned ->
                    mergeDependencies(returned)
                    _uiState.update {
                        it.copy(
                            showAddDialog = false,
                            editName = ""
                        )
                    }
                    _events.trySend(DepEvent.Message("$name 安装任务已提交"))
                    startStatusPolling(returned.mapNotNull(DependencyInfo::id).toSet())
                }
                .onFailure { e ->
                    _events.trySend(DepEvent.Message(e.message ?: "安装失败"))
                }
        }
    }

    fun showLog(dep: DependencyInfo) {
        val id = dep.id ?: return
        val name = dep.name ?: "依赖"
        viewModelScope.launch {
            _uiState.update { it.copy(logDepName = name, isLoadingLog = true, showLogSheet = true) }
            depRepo.getDependenceLog(id)
                .onSuccess { log ->
                    val window = log.boundedUtf8Tail()
                    _uiState.update {
                        it.copy(
                            logContent = window.content,
                            logTruncated = window.truncated,
                            logError = null,
                            isLoadingLog = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            logContent = null,
                            logTruncated = false,
                            logError = e.message ?: "未知错误",
                            isLoadingLog = false
                        )
                    }
                }
        }
    }

    fun dismissLog() {
        _uiState.update {
            it.copy(
                logContent = null,
                logTruncated = false,
                logError = null,
                logDepName = "",
                showLogSheet = false
            )
        }
    }

    private fun mergeDependencies(updates: List<DependencyInfo>) {
        if (updates.isEmpty()) return
        val updatesById = updates.mapNotNull { item -> item.id?.let { it to item } }.toMap()
        _uiState.update { state ->
            val existingIds = state.deps.mapNotNull(DependencyInfo::id).toSet()
            state.copy(
                deps = state.deps.map { dependency ->
                    dependency.id?.let(updatesById::get) ?: dependency
                } + updates.filter { it.id !in existingIds }
            )
        }
    }

    private fun startStatusPolling(ids: Set<Int>) {
        if (ids.isEmpty()) return
        statusPollJob?.cancel()
        statusPollJob = viewModelScope.launch {
            repeat(DEPENDENCY_STATUS_POLL_ATTEMPTS) {
                delay(DEPENDENCY_STATUS_POLL_DELAY_MS)
                val state = _uiState.value
                val result = depRepo.getDependencies(state.searchQuery, state.typeFilter)
                val dependencies = result.getOrNull() ?: return@repeat
                _uiState.update { it.copy(deps = dependencies) }
                val tracked = dependencies.filter { it.id?.let(ids::contains) == true }
                if (tracked.isEmpty() || tracked.none(DependencyInfo::isOperationActive)) {
                    return@launch
                }
            }
        }
    }

    private fun isDependencyOperationActive(id: Int): Boolean =
        _uiState.value.deps.firstOrNull { it.id == id }?.isOperationActive() == true

    private companion object {
        const val DEPENDENCY_STATUS_POLL_ATTEMPTS = 300
        const val DEPENDENCY_STATUS_POLL_DELAY_MS = 1_000L
    }
}
