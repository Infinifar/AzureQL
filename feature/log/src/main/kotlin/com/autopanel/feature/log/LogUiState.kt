package com.autopanel.feature.log

import com.autopanel.core.model.LogFile

data class LogUiState(
    val logs: List<LogFile> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val logContent: String? = null,
    val logFileName: String = "",
    val showLogSheet: Boolean = false,
    val isLoadingContent: Boolean = false,
    val error: String? = null
)
