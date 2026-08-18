package com.qinglong.app.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qinglong.core.domain.DashboardRepository
import com.qinglong.core.domain.LogRepository
import com.qinglong.core.model.DashboardOverview
import com.qinglong.core.model.DashboardSystem
import com.qinglong.core.model.LogFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val overview: DashboardOverview? = null,
    val system: DashboardSystem? = null,
    val logs: List<LogFile> = emptyList(),
    val isLoading: Boolean = false,
    val logFileName: String = "",
    val logContent: String? = null,
    val showLogSheet: Boolean = false,
    val isLoadingContent: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dashboardRepo: DashboardRepository,
    private val logRepo: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 每个接口独立 try/catch，单个失败不影响整体
            runCatching { dashboardRepo.getOverview() }
                .onSuccess { r ->
                    r.onSuccess { o -> _uiState.update { it.copy(overview = o) } }
                    r.onFailure { e -> Log.w("Home", "getOverview 失败: ${e.message}") }
                }
                .onFailure { e -> Log.w("Home", "getOverview 异常: ${e.message}") }

            runCatching { dashboardRepo.getSystem() }
                .onSuccess { r ->
                    r.onSuccess { s -> _uiState.update { it.copy(system = s) } }
                    r.onFailure { e -> Log.w("Home", "getSystem 失败: ${e.message}") }
                }
                .onFailure { e -> Log.w("Home", "getSystem 异常: ${e.message}") }

            runCatching { logRepo.getLogFiles() }
                .onSuccess { r ->
                    r.onSuccess { logs ->
                        _uiState.update { it.copy(logs = logs.sortedByDescending { l -> l.name }) }
                    }
                    r.onFailure { e -> Log.w("Home", "getLogFiles 失败: ${e.message}") }
                }
                .onFailure { e -> Log.w("Home", "getLogFiles 异常: ${e.message}") }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun showLog(log: LogFile) {
        val file = log.name ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(logFileName = file, isLoadingContent = true, showLogSheet = true) }
            logRepo.getLogContent(file, log.path ?: "")
                .onSuccess { c ->
                    _uiState.update { it.copy(logContent = c.ifEmpty { "暂无内容" }, isLoadingContent = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(logContent = "加载失败: ${e.message}", isLoadingContent = false) }
                }
        }
    }

    fun dismissLog() {
        _uiState.update { it.copy(logContent = null, logFileName = "", showLogSheet = false) }
    }
}
