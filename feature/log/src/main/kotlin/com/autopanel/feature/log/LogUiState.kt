package com.autopanel.feature.log

data class SystemLogUiState(
    val days: List<String> = emptyList(),
    val timezone: String? = null,
    val isInitializing: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedDay: String? = null,
    val content: String? = null,
    val totalBytes: Long = 0,
    val truncated: Boolean = false,
    val isLoadingContent: Boolean = false,
    val contentError: String? = null,
    val showLogSheet: Boolean = false
)

sealed interface SystemLogEvent {
    data class Message(val text: String) : SystemLogEvent
}
