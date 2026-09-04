package com.autopanel.feature.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.autopanel.core.domain.AuthRepository
import com.autopanel.core.domain.BackupRepository
import com.autopanel.core.model.BackupModule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.math.roundToInt

internal object BackupWorkerKeys {
    const val OPERATION = "operation"
    const val URI = "uri"
    const val MODULES = "modules"
    const val CONTENT_LENGTH = "content_length"
    const val MAX_BYTES = "max_bytes"
    const val STAGE = "stage"
    const val TRANSFERRED = "transferred"
    const val TOTAL = "total"
    const val HEALTH_ATTEMPT = "health_attempt"
    const val MESSAGE = "message"
    const val TAG_TRANSFER = "azureql_backup_transfer"
    const val TAG_EXPORT = "azureql_backup_export"
    const val TAG_IMPORT = "azureql_backup_import"
    const val TAG_RESTORE = "azureql_backup_restore"
}

private const val PROGRESS_STEP_BYTES = 256L * 1024L
private const val HEALTH_CHECK_ATTEMPTS = 30
private const val HEALTH_CHECK_DELAY_MS = 2_000L
private const val MAX_NETWORK_RETRIES = 3

@HiltWorker
internal class BackupTransferWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(appContext, params) {
    private val notifier = BackupWorkerNotifier(appContext, id.hashCode())
    private val kind = inputData.getString(BackupWorkerKeys.OPERATION)
        ?.let { runCatching { BackupWorkKind.valueOf(it) }.getOrNull() }
        ?: BackupWorkKind.EXPORT
    private val documentUri = inputData.getString(BackupWorkerKeys.URI)?.let(Uri::parse)

    override suspend fun doWork(): Result {
        val uri = documentUri ?: return failure("未取得目标文件位置")
        authRepository.getHost()
        authRepository.getToken()

        val initialStage = if (kind == BackupWorkKind.IMPORT) {
            BackupOperation.VALIDATING_IMPORT
        } else {
            BackupOperation.EXPORTING
        }
        publishProgress(initialStage, 0, contentLength())

        var deleteIncompleteExport = false
        try {
            val result = when (kind) {
                BackupWorkKind.EXPORT -> exportTo(uri)
                BackupWorkKind.IMPORT -> importFrom(uri)
                BackupWorkKind.RESTORE -> error("Invalid transfer operation")
            }
            val error = result.exceptionOrNull()
            if (error == null) {
                return success(
                    if (kind == BackupWorkKind.EXPORT) "备份已保存" else "备份上传完成，等待确认"
                )
            }
            if (error.isRetryable() && runAttemptCount < MAX_NETWORK_RETRIES) {
                return Result.retry()
            }
            deleteIncompleteExport = kind == BackupWorkKind.EXPORT
            return failure(
                safeBackupFailureMessage(
                    error,
                    if (kind == BackupWorkKind.EXPORT) "导出备份失败" else "上传备份失败"
                )
            )
        } catch (error: CancellationException) {
            deleteIncompleteExport = kind == BackupWorkKind.EXPORT
            throw error
        } catch (error: Exception) {
            if (error.isRetryable() && runAttemptCount < MAX_NETWORK_RETRIES) return Result.retry()
            deleteIncompleteExport = kind == BackupWorkKind.EXPORT
            return failure(safeBackupFailureMessage(error, "备份任务失败"))
        } finally {
            if (deleteIncompleteExport) deleteBackupDocument(applicationContext, uri)
        }
    }

    private suspend fun exportTo(uri: Uri): kotlin.Result<Unit> {
        val modules = inputData.getStringArray(BackupWorkerKeys.MODULES).orEmpty().toSet()
        val selected = BackupModule.entries.filterTo(mutableSetOf()) { it.apiValue in modules }
        val output = applicationContext.contentResolver.openOutputStream(uri, "rwt")
            ?: applicationContext.contentResolver.openOutputStream(uri, "w")
            ?: return kotlin.Result.failure(IOException("无法写入所选位置"))
        return output.use { destination ->
            backupRepository.exportBackup(selected, destination) { transferred, total ->
                ensureNotStopped()
                publishProgressAsync(BackupOperation.EXPORTING, transferred, total)
            }
        }
    }

    private suspend fun importFrom(uri: Uri): kotlin.Result<Unit> {
        val length = contentLength()
        val maxBytes = inputData.getLong(BackupWorkerKeys.MAX_BYTES, -1L)
        if (maxBytes <= 0) return kotlin.Result.failure(IllegalArgumentException("备份大小上限无效"))
        if (length != null && length > maxBytes) {
            return kotlin.Result.failure(IllegalArgumentException("备份文件超过大小上限，未开始上传"))
        }
        val input = applicationContext.contentResolver.openInputStream(uri)
            ?: return kotlin.Result.failure(IOException("无法读取所选备份文件"))
        return input.use { source ->
            backupRepository.importBackup(source, length) { transferred, total ->
                ensureNotStopped()
                if (transferred > maxBytes) throw IllegalArgumentException("备份数据超过大小上限，上传已中止")
                publishProgressAsync(BackupOperation.IMPORTING, transferred, total)
            }
        }
    }

    private suspend fun publishProgress(stage: BackupOperation, transferred: Long, total: Long?) {
        val data = progressData(stage, transferred, total)
        setProgress(data)
        setForeground(notifier.foregroundInfo(stage, transferred, total, id, canCancel = true))
    }

    private var lastProgressBytes = -PROGRESS_STEP_BYTES

    private fun publishProgressAsync(stage: BackupOperation, transferred: Long, total: Long?) {
        if (transferred - lastProgressBytes < PROGRESS_STEP_BYTES && transferred != total) return
        lastProgressBytes = transferred
        val data = progressData(stage, transferred, total)
        setProgressAsync(data)
        setForegroundAsync(notifier.foregroundInfo(stage, transferred, total, id, canCancel = true))
    }

    private fun progressData(stage: BackupOperation, transferred: Long, total: Long?) =
        Data.Builder()
            .putString(BackupWorkerKeys.STAGE, stage.name)
            .putLong(BackupWorkerKeys.TRANSFERRED, transferred)
            .putLong(BackupWorkerKeys.TOTAL, total ?: -1L)
            .build()

    private fun contentLength(): Long? = inputData
        .getLong(BackupWorkerKeys.CONTENT_LENGTH, -1L)
        .takeIf { it >= 0 }

    private fun success(message: String): Result = Result.success(
        Data.Builder()
            .putString(BackupWorkerKeys.STAGE, initialTerminalStage().name)
            .putString(BackupWorkerKeys.MESSAGE, message)
            .build()
    )

    private fun failure(message: String): Result = Result.failure(
        Data.Builder()
            .putString(BackupWorkerKeys.STAGE, initialTerminalStage().name)
            .putString(BackupWorkerKeys.MESSAGE, message)
            .build()
    )

    private fun initialTerminalStage() = if (kind == BackupWorkKind.IMPORT) {
        BackupOperation.IMPORTING
    } else {
        BackupOperation.EXPORTING
    }

    private fun ensureNotStopped() {
        if (isStopped) throw CancellationException("备份任务已取消")
    }

}

@HiltWorker
internal class BackupRestoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(appContext, params) {
    private val notifier = BackupWorkerNotifier(appContext, id.hashCode())

    override suspend fun doWork(): Result {
        authRepository.getHost()
        authRepository.getToken()
        publish(BackupOperation.ACTIVATING_RESTORE, 0)

        val activation = backupRepository.activateImportedBackup()
        if (activation.isFailure) {
            return failure(
                safeBackupFailureMessage(
                    activation.exceptionOrNull(),
                    "数据激活失败，请确认备份与服务器版本兼容"
                )
            )
        }

        val recoveredAt = awaitHealthyService(
            attempts = HEALTH_CHECK_ATTEMPTS,
            delayMillis = HEALTH_CHECK_DELAY_MS,
            healthCheck = { backupRepository.healthCheck().isSuccess },
            onAttempt = { attempt -> publish(BackupOperation.WAITING_FOR_SERVICE, attempt) }
        )
        if (recoveredAt != null) {
            authRepository.clearCredentials()
            return Result.success(
                Data.Builder()
                    .putString(BackupWorkerKeys.STAGE, BackupOperation.WAITING_FOR_SERVICE.name)
                    .putInt(BackupWorkerKeys.HEALTH_ATTEMPT, recoveredAt)
                    .putString(BackupWorkerKeys.MESSAGE, "服务已恢复，请重新登录")
                    .build()
            )
        }
        return failure("60 秒内未检测到服务恢复；请检查容器状态，必要时执行 ql reload data")
    }

    private suspend fun publish(stage: BackupOperation, attempt: Int) {
        val data = Data.Builder()
            .putString(BackupWorkerKeys.STAGE, stage.name)
            .putInt(BackupWorkerKeys.HEALTH_ATTEMPT, attempt)
            .build()
        setProgress(data)
        setForeground(notifier.foregroundInfo(stage, attempt.toLong(), HEALTH_CHECK_ATTEMPTS.toLong(), id, false))
    }

    private fun failure(message: String): Result = Result.failure(
        Data.Builder()
            .putString(BackupWorkerKeys.STAGE, BackupOperation.WAITING_FOR_SERVICE.name)
            .putString(BackupWorkerKeys.MESSAGE, message)
            .build()
    )
}

internal suspend fun awaitHealthyService(
    attempts: Int,
    delayMillis: Long,
    healthCheck: suspend () -> Boolean,
    onAttempt: suspend (Int) -> Unit
): Int? {
    require(attempts > 0) { "健康检查次数必须大于 0" }
    require(delayMillis >= 0) { "健康检查间隔不能小于 0" }
    repeat(attempts) { index ->
        delay(delayMillis)
        val attempt = index + 1
        onAttempt(attempt)
        if (healthCheck()) return attempt
    }
    return null
}

internal fun safeBackupFailureMessage(error: Throwable?, fallback: String): String {
    if (error == null) return fallback
    val exceptionName = error.javaClass.simpleName
    if (exceptionName.contains("Serialization", ignoreCase = true) ||
        exceptionName.contains("Json", ignoreCase = true)
    ) {
        return "服务器响应解析失败，请确认青龙版本兼容"
    }
    if (error is IllegalArgumentException) return "备份格式错误，请选择有效且完整的 .tgz/.gz 文件"
    if (error.isRetryable()) return "网络连接失败，请检查服务器状态和网络后重试"

    val httpCode = HTTP_CODE.find(error.message.orEmpty())?.groupValues?.getOrNull(1)
    return if (httpCode != null) "$fallback（HTTP $httpCode）" else fallback
}

private class BackupWorkerNotifier(
    private val context: Context,
    private val notificationId: Int
) {
    init {
        val manager = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, text("备份与恢复", "Backup and restore"), NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun foregroundInfo(
        stage: BackupOperation,
        transferred: Long,
        total: Long?,
        workId: java.util.UUID,
        canCancel: Boolean
    ): ForegroundInfo {
        val title = when (stage) {
            BackupOperation.EXPORTING -> text("正在导出备份", "Exporting backup")
            BackupOperation.VALIDATING_IMPORT -> text("正在校验备份", "Validating backup")
            BackupOperation.IMPORTING -> text("正在上传备份", "Uploading backup")
            BackupOperation.ACTIVATING_RESTORE -> text("正在激活恢复数据", "Activating restored data")
            BackupOperation.WAITING_FOR_SERVICE -> text("正在等待青龙服务恢复", "Waiting for QingLong")
        }
        val progress = total?.takeIf { it > 0 }?.let {
            (transferred.toDouble() / it.toDouble() * 100).coerceIn(0.0, 100.0).roundToInt()
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (stage == BackupOperation.EXPORTING) android.R.drawable.stat_sys_download
                else android.R.drawable.stat_sys_upload
            )
            .setContentTitle(title)
            .setContentText(text("可安全离开备份页面", "You can safely leave the backup page"))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress ?: 0, progress == null)

        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        if (canCancel) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                text("取消", "Cancel"),
                WorkManager.getInstance(context).createCancelPendingIntent(workId)
            )
        }
        return ForegroundInfo(
            notificationId,
            builder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun text(chinese: String, english: String): String =
        if (context.resources.configuration.locales[0].language == "en") english else chinese

    private companion object {
        const val CHANNEL_ID = "azureql_backup_transfer"
    }
}

private fun Throwable.isRetryable(): Boolean =
    generateSequence(this) { it.cause }.any { it is IOException }

private val HTTP_CODE = Regex("(?i)HTTP\\s*(\\d{3})")
