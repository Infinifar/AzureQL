package com.qinglong.feature.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qinglong.core.domain.LogRepository
import com.qinglong.core.model.LogFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepo: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    init { loadLogFiles() }

    fun loadLogFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoading = true) }
            logRepo.getLogFiles()
                .onSuccess { list ->
                    val sorted = list.sortedByDescending { it.name }
                    _uiState.update {
                        it.copy(logs = sorted, isRefreshing = false, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, isLoading = false, error = e.message)
                    }
                }
        }
    }

    fun refresh() = loadLogFiles()
    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun showLog(log: LogFile) {
        val file = log.name ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(logFileName = file, isLoadingContent = true, showLogSheet = true) }
            logRepo.getLogContent(file, log.path ?: "")
                .onSuccess { content ->
                    _uiState.update {
                        it.copy(logContent = content.ifEmpty { "暂无内容" }, isLoadingContent = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(logContent = "加载失败: ${e.message}", isLoadingContent = false)
                    }
                }
        }
    }

    fun dismissLog() {
        _uiState.update { it.copy(logContent = null, logFileName = "", showLogSheet = false) }
    }
}
