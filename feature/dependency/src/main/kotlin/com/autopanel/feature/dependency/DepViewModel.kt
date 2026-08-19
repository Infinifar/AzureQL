package com.autopanel.feature.dependency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.DependencyRepository
import com.autopanel.core.model.DependencyInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DepViewModel @Inject constructor(
    private val depRepo: DependencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DepUiState())
    val uiState: StateFlow<DepUiState> = _uiState.asStateFlow()

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
                        it.copy(isRefreshing = false, isLoading = false, error = e.message)
                    }
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
            if (it.selectedIds.size == it.deps.size) it.copy(selectedIds = emptySet())
            else it.copy(selectedIds = it.deps.mapNotNull { d -> d.id }.toSet())
        }
    }

    fun batchDelete(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            depRepo.deleteDependencies(ids)
                .onFailure { e -> _uiState.update { it.copy(error = "删除失败: ${e.message}") } }
            _uiState.update { it.copy(isBatchMode = false, selectedIds = emptySet()) }
            loadDeps()
        }
    }

    fun batchDeleteSelected() = batchDelete(_uiState.value.selectedIds.toList())

    fun batchReinstall(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            depRepo.reinstallDependencies(ids)
                .onSuccess {
                    _uiState.update {
                        it.copy(isBatchMode = false, selectedIds = emptySet(), successMessage = "重新安装已提交")
                    }
                    loadDeps()
                }
                .onFailure { e -> _uiState.update { it.copy(error = "重新安装失败: ${e.message}") } }
        }
    }

    fun batchReinstallSelected() = batchReinstall(_uiState.value.selectedIds.toList())

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
            depRepo.addDependencies(listOf(Pair(name, s.editType)))
                .onSuccess {
                    _uiState.update {
                        it.copy(showAddDialog = false, editName = "", successMessage = "已添加 $name")
                    }
                    loadDeps()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
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
                    _uiState.update {
                        it.copy(logContent = log.ifEmpty { "暂无日志" }, isLoadingLog = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(logContent = "加载失败: ${e.message}", isLoadingLog = false)
                    }
                }
        }
    }

    fun dismissLog() {
        _uiState.update { it.copy(logContent = null, logDepName = "", showLogSheet = false) }
    }
}
