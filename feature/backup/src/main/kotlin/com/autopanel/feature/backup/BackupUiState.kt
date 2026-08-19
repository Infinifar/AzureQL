package com.autopanel.feature.backup

import com.autopanel.core.model.BackupModule

enum class BackupOperation {
    EXPORTING,
    IMPORTING,
    RESTORING
}

data class BackupUiState(
    val selectedModules: Set<BackupModule> = setOf(
        BackupModule.BASE,
        BackupModule.CONFIG,
        BackupModule.SCRIPTS,
        BackupModule.DEPENDENCIES
    ),
    val operation: BackupOperation? = null,
    val showRestoreConfirmation: Boolean = false,
    val healthCheckAttempt: Int = 0
) {
    val isBusy: Boolean get() = operation != null
}

sealed interface BackupEvent {
    data class Message(val value: String) : BackupEvent
    data object RestoreCompleted : BackupEvent
}
