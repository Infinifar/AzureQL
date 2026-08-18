package com.qinglong.feature.dependency

import com.qinglong.core.model.DependencyInfo

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
    val logDepName: String = "",
    val showLogSheet: Boolean = false,
    val isLoadingLog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
