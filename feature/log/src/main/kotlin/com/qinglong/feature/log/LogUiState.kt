package com.qinglong.feature.log

import com.qinglong.core.model.LogFile

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
