package com.autopanel.feature.task

import com.autopanel.core.model.TaskInfo

data class TaskUiState(
    val tasks: List<TaskInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searchQuery: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val isBatchMode: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val editingTask: TaskInfo? = null,
    val showEditDialog: Boolean = false,
    val logContent: String? = null,
    val showLogSheet: Boolean = false,
    val duplicateTask: TaskInfo? = null,
    val showDuplicateDialog: Boolean = false
)

sealed interface TaskEvent {
    data class Message(val text: String) : TaskEvent
}
