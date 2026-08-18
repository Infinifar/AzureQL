package com.qinglong.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qinglong.core.domain.LogRepository
import com.qinglong.core.domain.TaskRepository
import com.qinglong.core.model.LogFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val runningCount: Int = 0,
    val idleCount: Int = 0,
    val logs: List<LogFile> = emptyList(),
    val isLoading: Boolean = false,
    val logFileName: String = "",
    val logContent: String? = null,
    val showLogSheet: Boolean = false,
    val isLoadingContent: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val logRepo: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            taskRepo.getTasks("", 1, 200)
                .onSuccess { (list, _) ->
                    val running = list.count { t -> t.statusCode == 0 }
                    val idle = list.count { t -> t.statusCode == 2 }
                    _uiState.update { it.copy(runningCount = running, idleCount = idle) }
                }
            logRepo.getLogFiles()
                .onSuccess { logs ->
                    _uiState.update { it.copy(logs = logs.sortedByDescending { l -> l.name }) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun showLog(log: LogFile) {
        val file = log.name ?: return
        val name = log.name
        viewModelScope.launch {
            _uiState.update { it.copy(logFileName = name, isLoadingContent = true, showLogSheet = true) }
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
