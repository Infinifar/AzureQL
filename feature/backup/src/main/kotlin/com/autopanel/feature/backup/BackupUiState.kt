package com.autopanel.feature.backup

import com.autopanel.core.model.BackupModule

enum class BackupOperation {
    EXPORTING,
    VALIDATING_IMPORT,
    IMPORTING,
    ACTIVATING_RESTORE,
    WAITING_FOR_SERVICE;

    val canCancel: Boolean
        get() = this == EXPORTING || this == VALIDATING_IMPORT || this == IMPORTING
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
    val healthCheckAttempt: Int = 0,
    val transferredBytes: Long = 0,
    val totalBytes: Long? = null,
    val maxImportSizeMb: String = "1024"
) {
    val isBusy: Boolean get() = operation != null
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let {
            (transferredBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

sealed interface BackupEvent {
    data class Message(val value: String) : BackupEvent
    data object RestoreCompleted : BackupEvent
}
