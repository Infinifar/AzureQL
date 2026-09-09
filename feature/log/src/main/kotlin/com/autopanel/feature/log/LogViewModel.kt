package com.autopanel.feature.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.ConfigRepository
import com.autopanel.core.domain.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SystemLogViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val logRepository: LogRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SystemLogUiState())
    val uiState: StateFlow<SystemLogUiState> = _uiState.asStateFlow()
    private val _events = Channel<SystemLogEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var loadJob: Job? = null

    init { loadCalendar() }

    fun loadCalendar() {
        viewModelScope.launch {
            _uiState.update { it.copy(isInitializing = true) }
            val timezone = configRepository.getSystemConfig().getOrNull()
                ?.timezone?.takeIf(String::isNotBlank)
            _uiState.update {
                it.copy(
                    days = recentSystemLogDays(timezone),
                    timezone = timezone,
                    isInitializing = false
                )
            }
        }
    }

    fun showDay(day: String) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedDay = day,
                content = null,
                totalBytes = 0,
                truncated = false,
                contentError = null,
                isLoadingContent = true,
                showLogSheet = true
            )
        }
        loadJob = viewModelScope.launch {
            logRepository.getSystemLog(day)
                .onSuccess { log ->
                    _uiState.update {
                        it.copy(
                            content = log.content,
                            totalBytes = log.totalBytes,
                            truncated = log.truncated,
                            contentError = null,
                            isLoadingContent = false,
                            isRefreshing = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            contentError = error.message ?: "获取系统日志失败",
                            isLoadingContent = false,
                            isRefreshing = false
                        )
                    }
                }
        }
    }

    fun refresh() {
        val day = _uiState.value.selectedDay
        if (day == null) loadCalendar() else {
            _uiState.update { it.copy(isRefreshing = true) }
            showDay(day)
        }
    }

    fun dismissLog() {
        loadJob?.cancel()
        _uiState.update { it.copy(showLogSheet = false, isRefreshing = false) }
    }
}

internal fun recentSystemLogDays(
    timezone: String?,
    today: LocalDate = LocalDate.now(resolveZone(timezone))
): List<String> = List(7) { offset -> today.minusDays(offset.toLong()).toString() }

private fun resolveZone(timezone: String?): ZoneId = runCatching {
    timezone?.takeIf(String::isNotBlank)?.let(ZoneId::of) ?: ZoneId.systemDefault()
}.getOrDefault(ZoneId.systemDefault())
