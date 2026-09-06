package com.autopanel.feature.backup

import com.autopanel.core.model.BackupModule

enum class BackupOperation {
    EXPORTING,
    UPLOADING_NETWORK,
    VALIDATING_IMPORT,
    IMPORTING,
    ACTIVATING_RESTORE,
    WAITING_FOR_SERVICE;

    val canCancel: Boolean
        get() = this == EXPORTING || this == UPLOADING_NETWORK ||
            this == VALIDATING_IMPORT || this == IMPORTING
}

enum class NetworkStorageProvider { WEBDAV, S3 }

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
    val maxImportSizeMb: String = "1024",
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val webDavRemoteDirectory: String = DEFAULT_WEBDAV_DIRECTORY,
    val webDavHasSavedPassword: Boolean = false,
    val webDavConfigured: Boolean = false,
    val webDavDirty: Boolean = false,
    val isTestingWebDav: Boolean = false,
    val s3Endpoint: String = "",
    val s3AccessKeyId: String = "",
    val s3SecretAccessKey: String = "",
    val s3Bucket: String = "",
    val s3Region: String = DEFAULT_S3_REGION,
    val s3PathStyle: Boolean = true,
    val s3RemoteDirectory: String = DEFAULT_S3_DIRECTORY,
    val s3HasSavedAccessKey: Boolean = false,
    val s3HasSavedSecretKey: Boolean = false,
    val s3Configured: Boolean = false,
    val s3Dirty: Boolean = false,
    val isTestingS3: Boolean = false
) {
    val isBusy: Boolean get() = operation != null
    val configuredNetworkProviders: Set<NetworkStorageProvider>
        get() = buildSet {
            if (webDavConfigured && !webDavDirty) add(NetworkStorageProvider.WEBDAV)
            if (s3Configured && !s3Dirty) add(NetworkStorageProvider.S3)
        }
    val canExportToNetwork: Boolean
        get() = configuredNetworkProviders.isNotEmpty() && !isBusy && !isTestingWebDav && !isTestingS3
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let {
            (transferredBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

sealed interface BackupEvent {
    data class Message(val value: String) : BackupEvent
    data object RestoreCompleted : BackupEvent
}
