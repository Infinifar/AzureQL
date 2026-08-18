package com.qinglong.feature.env

import com.qinglong.core.model.EnvInfo

data class EnvUiState(
    val envs: List<EnvInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val isBatchMode: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val editingEnv: EnvInfo? = null,
    val showEditDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val importText: String = "",
    // 去重
    val duplicateEnv: EnvInfo? = null,
    val showDuplicateDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
