package com.autopanel.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.DashboardRepository
import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardSystem
import com.autopanel.core.model.DashboardTrendItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

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
}
