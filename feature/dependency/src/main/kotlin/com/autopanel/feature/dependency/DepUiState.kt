package com.autopanel.feature.dependency

import com.autopanel.core.model.DependencyInfo
import com.autopanel.core.model.DependencyStatus

internal fun DependencyInfo.isOperationActive(): Boolean =
    status == DependencyStatus.QUEUED ||
        status == DependencyStatus.INSTALLING ||
        status == DependencyStatus.UNINSTALLING

data class DepUiState(
    val deps: List<DependencyInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val typeFilter: String = "",
    val isBatchMode: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val showAddDialog: Boolean = false,
    val editName: String = "",
    val editType: String = "nodejs",
    val logContent: String? = null,
    val logTruncated: Boolean = false,
    val logError: String? = null,
    val logDepName: String = "",
    val showLogSheet: Boolean = false,
    val isLoadingLog: Boolean = false,
    val confirmReinstall: DependencyInfo? = null,
    val confirmDelete: DependencyInfo? = null,
    val isMutating: Boolean = false
)

sealed interface DepEvent {
    data class Message(val text: String) : DepEvent
}
