package com.autopanel.feature.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal enum class BackupWorkKind { EXPORT, IMPORT, RESTORE }

internal enum class BackupWorkStatus { ENQUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

internal data class BackupWorkSnapshot(
    val id: String,
    val kind: BackupWorkKind,
    val status: BackupWorkStatus,
    val operation: BackupOperation,
    val transferredBytes: Long = 0,
    val totalBytes: Long? = null,
    val healthCheckAttempt: Int = 0,
    val message: String? = null
) {
    val isActive: Boolean get() = status == BackupWorkStatus.ENQUEUED || status == BackupWorkStatus.RUNNING
}

internal interface BackupWorkController {
    val transfer: Flow<BackupWorkSnapshot?>
    val restore: Flow<BackupWorkSnapshot?>

    fun startExport(destinationUri: String, modules: Set<String>): String
    fun startImport(sourceUri: String, contentLength: Long?, maxBytes: Long): String
    fun cancelTransfer()
    fun startRestore(): String
}

@Singleton
internal class WorkManagerBackupWorkController @Inject constructor(
    @ApplicationContext context: Context
) : BackupWorkController {
    private val workManager = WorkManager.getInstance(context)

    override val transfer: Flow<BackupWorkSnapshot?> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_TRANSFER)
            .map { workInfos -> workInfos.lastOrNull()?.toSnapshot() }
            .distinctUntilChanged()

    override val restore: Flow<BackupWorkSnapshot?> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_RESTORE)
            .map { workInfos -> workInfos.lastOrNull()?.toSnapshot(BackupWorkKind.RESTORE) }
            .distinctUntilChanged()

    override fun startExport(destinationUri: String, modules: Set<String>): String {
        val request = OneTimeWorkRequestBuilder<BackupTransferWorker>()
            .setInputData(
                Data.Builder()
                    .putString(BackupWorkerKeys.OPERATION, BackupWorkKind.EXPORT.name)
                    .putString(BackupWorkerKeys.URI, destinationUri)
                    .putStringArray(BackupWorkerKeys.MODULES, modules.toTypedArray())
                    .build()
            )
            .addTag(BackupWorkerKeys.TAG_TRANSFER)
            .addTag(BackupWorkerKeys.TAG_EXPORT)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_TRANSFER, ExistingWorkPolicy.REPLACE, request)
        return request.id.toString()
    }

    override fun startImport(sourceUri: String, contentLength: Long?, maxBytes: Long): String {
        val request = OneTimeWorkRequestBuilder<BackupTransferWorker>()
            .setInputData(
                Data.Builder()
                    .putString(BackupWorkerKeys.OPERATION, BackupWorkKind.IMPORT.name)
                    .putString(BackupWorkerKeys.URI, sourceUri)
                    .putLong(BackupWorkerKeys.CONTENT_LENGTH, contentLength ?: -1L)
                    .putLong(BackupWorkerKeys.MAX_BYTES, maxBytes)
                    .build()
            )
            .addTag(BackupWorkerKeys.TAG_TRANSFER)
            .addTag(BackupWorkerKeys.TAG_IMPORT)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_TRANSFER, ExistingWorkPolicy.REPLACE, request)
        return request.id.toString()
    }

    override fun cancelTransfer() {
        workManager.cancelUniqueWork(UNIQUE_TRANSFER)
    }

    override fun startRestore(): String {
        val request = OneTimeWorkRequestBuilder<BackupRestoreWorker>()
            .addTag(BackupWorkerKeys.TAG_RESTORE)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_RESTORE, ExistingWorkPolicy.REPLACE, request)
        return request.id.toString()
    }

    private fun WorkInfo.toSnapshot(forcedKind: BackupWorkKind? = null): BackupWorkSnapshot {
        val kind = forcedKind ?: when {
            BackupWorkerKeys.TAG_IMPORT in tags -> BackupWorkKind.IMPORT
            else -> BackupWorkKind.EXPORT
        }
        val workData = if (state.isFinished) outputData else progress
        val fallbackOperation = when (kind) {
            BackupWorkKind.EXPORT -> BackupOperation.EXPORTING
            BackupWorkKind.IMPORT -> BackupOperation.VALIDATING_IMPORT
            BackupWorkKind.RESTORE -> BackupOperation.ACTIVATING_RESTORE
        }
        return BackupWorkSnapshot(
            id = id.toString(),
            kind = kind,
            status = when (state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> BackupWorkStatus.ENQUEUED
                WorkInfo.State.RUNNING -> BackupWorkStatus.RUNNING
                WorkInfo.State.SUCCEEDED -> BackupWorkStatus.SUCCEEDED
                WorkInfo.State.FAILED -> BackupWorkStatus.FAILED
                WorkInfo.State.CANCELLED -> BackupWorkStatus.CANCELLED
            },
            operation = workData.getString(BackupWorkerKeys.STAGE)
                ?.let { runCatching { BackupOperation.valueOf(it) }.getOrNull() }
                ?: fallbackOperation,
            transferredBytes = workData.getLong(BackupWorkerKeys.TRANSFERRED, 0L),
            totalBytes = workData.getLong(BackupWorkerKeys.TOTAL, -1L).takeIf { it >= 0 },
            healthCheckAttempt = workData.getInt(BackupWorkerKeys.HEALTH_ATTEMPT, 0),
            message = workData.getString(BackupWorkerKeys.MESSAGE)
        )
    }

    private companion object {
        const val UNIQUE_TRANSFER = "azureql_backup_transfer"
        const val UNIQUE_RESTORE = "azureql_backup_restore"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BackupWorkModule {
    @Binds
    abstract fun bindBackupWorkController(
        implementation: WorkManagerBackupWorkController
    ): BackupWorkController
}
