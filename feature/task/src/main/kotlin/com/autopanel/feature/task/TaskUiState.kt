package com.autopanel.feature.task

import com.autopanel.core.model.TaskInfo

data class TaskLabelSummary(
    val name: String,
    val referenceCount: Int
)

data class TaskUiState(
    val tasks: List<TaskInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searchQuery: String = "",
    val availableLabels: List<String> = emptyList(),
    val labelSummaries: List<TaskLabelSummary> = emptyList(),
    val isLoadingLabelSummaries: Boolean = false,
    val isUpdatingLabel: Boolean = false,
    val selectedLabels: Set<String> = emptySet(),
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val isBatchMode: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val editingTask: TaskInfo? = null,
    val showEditDialog: Boolean = false,
    val logContent: String? = null,
    val logTruncated: Boolean = false,
    val logError: String? = null,
    val showLogSheet: Boolean = false,
    val duplicateTask: TaskInfo? = null,
    val showDuplicateDialog: Boolean = false
)

sealed interface TaskEvent {
    data class Message(val text: String) : TaskEvent
}
