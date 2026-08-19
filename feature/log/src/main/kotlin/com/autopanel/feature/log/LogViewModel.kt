package com.autopanel.feature.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.LogRepository
import com.autopanel.core.model.LogFile
import com.autopanel.core.model.flattenLogFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepo: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    private val _events = Channel<LogEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { loadLogFiles() }

    fun loadLogFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoading = true) }
            logRepo.getLogFiles()
                .onSuccess { list ->
                    val sorted = flattenLogFiles(list).sortedByDescending { it.title }
                    _uiState.update {
                        it.copy(logs = sorted, isRefreshing = false, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, isLoading = false)
                    }
                    _events.trySend(LogEvent.Message(e.message ?: "加载日志失败"))
                }
        }
    }

    fun refresh() = loadLogFiles()
    fun showLog(log: LogFile) {
        val file = log.title ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(logFileName = file, isLoadingContent = true, showLogSheet = true) }
            logRepo.getLogContent(file, log.parent ?: "")
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

    fun requestDelete(log: LogFile) {
        _uiState.update { it.copy(confirmDelete = log) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(confirmDelete = null) }
    }

    fun confirmDelete() {
        val log = _uiState.value.confirmDelete ?: return
        _uiState.update { it.copy(confirmDelete = null, isDeleting = true) }
        viewModelScope.launch {
            logRepo.deleteLog(log)
                .onSuccess {
                    _uiState.update { it.copy(isDeleting = false) }
                    _events.trySend(LogEvent.Message("日志已删除"))
                    loadLogFiles()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isDeleting = false) }
                    _events.trySend(LogEvent.Message(error.message ?: "删除日志失败"))
                }
        }
    }
}
