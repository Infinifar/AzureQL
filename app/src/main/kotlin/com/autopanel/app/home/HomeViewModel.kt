package com.autopanel.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.DashboardRepository
import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardSystem
import com.autopanel.core.model.DashboardRuntime
import com.autopanel.core.model.DashboardTopCountItem
import com.autopanel.core.model.DashboardTopTimeItem
import com.autopanel.core.model.DashboardTrendItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val overview: DashboardOverview? = null,
    val system: DashboardSystem? = null,
    val trend: List<DashboardTrendItem> = emptyList(),
    val runtime: DashboardRuntime? = null,
    val topCount: List<DashboardTopCountItem> = emptyList(),
    val topTime: List<DashboardTopTimeItem> = emptyList(),
    val showTaskDetails: Boolean = false,
    val isTaskDetailsLoading: Boolean = false,
    val taskDetailsError: String? = null,
    val isLoading: Boolean = false,
    val showRestartConfirm: Boolean = false,
    val restartMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dashboardRepo: DashboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var taskDetailsJob: Job? = null

    init { loadDashboard(includeCache = true) }

    fun refresh() {
        loadDashboard(includeCache = false)
    }

    private fun loadDashboard(includeCache: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            if (includeCache) {
                supervisorScope {
                    val overview = async { dashboardRepo.getCachedOverview() }
                    val system = async { dashboardRepo.getCachedSystem() }
                    val trend = async { dashboardRepo.getCachedTrend(7) }
                    overview.await()?.let { value ->
                        _uiState.update { it.copy(overview = value) }
                    }
                    system.await()?.let { value ->
                        _uiState.update { it.copy(system = value) }
                    }
                    trend.await()?.let { value ->
                        _uiState.update { it.copy(trend = value) }
                    }
                }
            }

            supervisorScope {
                val overview = async { dashboardRepo.getOverview() }
                val system = async { dashboardRepo.getSystem() }
                val trend = async { dashboardRepo.getTrend(7) }
                overview.await().onSuccess { value ->
                    _uiState.update { it.copy(overview = value) }
                }
                system.await().onSuccess { value ->
                    _uiState.update { it.copy(system = value) }
                }
                trend.await().onSuccess { value ->
                    _uiState.update { it.copy(trend = value) }
                }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // ── 重启青龙 ──

    fun requestRestart() {
        _uiState.update { it.copy(showRestartConfirm = true) }
    }

    fun dismissRestartConfirm() {
        _uiState.update { it.copy(showRestartConfirm = false) }
    }

    fun confirmRestart() {
        _uiState.update { it.copy(showRestartConfirm = false) }
        viewModelScope.launch {
            dashboardRepo.reloadSystem()
                .onSuccess { _uiState.update { it.copy(restartMessage = "重启指令已发送，青龙即将重启") } }
                .onFailure { e -> _uiState.update { it.copy(restartMessage = "重启失败：${e.message}") } }
        }
    }

    fun clearRestartMessage() {
        _uiState.update { it.copy(restartMessage = null) }
    }

    fun showTaskDetails() {
        if (_uiState.value.showTaskDetails) return
        _uiState.update { it.copy(showTaskDetails = true) }
        loadTaskDetails()
    }

    fun dismissTaskDetails() {
        taskDetailsJob?.cancel()
        taskDetailsJob = null
        _uiState.update {
            it.copy(
                showTaskDetails = false,
                isTaskDetailsLoading = false,
                taskDetailsError = null
            )
        }
    }

    fun refreshTaskDetails() {
        if (_uiState.value.showTaskDetails) loadTaskDetails()
    }

    private fun loadTaskDetails() {
        taskDetailsJob?.cancel()
        taskDetailsJob = viewModelScope.launch {
            _uiState.update { it.copy(isTaskDetailsLoading = true, taskDetailsError = null) }
            supervisorScope {
                val runtime = async { dashboardRepo.getCachedRuntime() }
                val topCount = async { dashboardRepo.getCachedTopCount() }
                val topTime = async { dashboardRepo.getCachedTopTime() }
                runtime.await()?.let { value ->
                    _uiState.update { it.copy(runtime = value) }
                }
                topCount.await()?.let { value ->
                    _uiState.update { it.copy(topCount = value) }
                }
                topTime.await()?.let { value ->
                    _uiState.update { it.copy(topTime = value) }
                }
            }
            supervisorScope {
                val runtime = async { dashboardRepo.getRuntime() }
                val topCount = async { dashboardRepo.getTopCount() }
                val topTime = async { dashboardRepo.getTopTime() }
                val runtimeResult = runtime.await()
                val topCountResult = topCount.await()
                val topTimeResult = topTime.await()

                runtimeResult.onSuccess { value ->
                    _uiState.update { it.copy(runtime = value) }
                }
                topCountResult.onSuccess { value ->
                    _uiState.update { it.copy(topCount = value) }
                }
                topTimeResult.onSuccess { value ->
                    _uiState.update { it.copy(topTime = value) }
                }

                val errors = listOfNotNull(
                    runtimeResult.exceptionOrNull()?.message,
                    topCountResult.exceptionOrNull()?.message,
                    topTimeResult.exceptionOrNull()?.message
                ).distinct()
                _uiState.update {
                    it.copy(
                        isTaskDetailsLoading = false,
                        taskDetailsError = errors.takeIf(List<String>::isNotEmpty)?.joinToString("；")
                    )
                }
            }
        }
    }
}
