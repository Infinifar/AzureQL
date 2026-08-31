package com.autopanel.feature.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.autopanel.core.mcp.McpServerEngine
import com.autopanel.core.mcp.McpServerState
import com.autopanel.core.mcp.McpOperationManager
import com.autopanel.core.mcp.McpOperationState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class McpForegroundService : Service() {
    @Inject lateinit var engine: McpServerEngine
    @Inject lateinit var operationManager: McpOperationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var operationObserver: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        startForegroundCompat(createNotification(0))

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (engine.state.value !is McpServerState.Running && engine.state.value != McpServerState.Starting) {
            serviceScope.launch {
                try {
                    engine.start()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    stopSelf(startId)
                }
            }
        }
        if (operationObserver == null) {
            operationObserver = serviceScope.launch {
                operationManager.operations
                    .map { operations -> operations.count { it.state == McpOperationState.WAITING_CONFIRMATION } }
                    .distinctUntilChanged()
                    .collect { pendingCount -> updateOperationNotifications(pendingCount) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (engine.state.value !is McpServerState.Failed && engine.state.value != McpServerState.Stopped) {
            runBlocking(Dispatchers.IO) {
                runCatching { withTimeout(SHUTDOWN_TIMEOUT_MS) { engine.stop() } }
            }
        }
        getSystemService(NotificationManager::class.java)?.cancel(APPROVAL_NOTIFICATION_ID)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createNotification(pendingCount: Int): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, McpForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.mcp_notification_title))
            .setContentText(
                if (pendingCount > 0) getString(R.string.mcp_notification_pending, pendingCount)
                else getString(R.string.mcp_notification_text)
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.mcp_notification_stop), stopIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.mcp_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.mcp_notification_channel_description)
                }
            )
        }
        if (manager.getNotificationChannel(APPROVAL_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    APPROVAL_CHANNEL_ID,
                    getString(R.string.mcp_approval_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun updateOperationNotifications(pendingCount: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, createNotification(pendingCount))
        if (pendingCount == 0) {
            manager.cancel(APPROVAL_NOTIFICATION_ID)
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                2,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        manager.notify(
            APPROVAL_NOTIFICATION_ID,
            NotificationCompat.Builder(this, APPROVAL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(R.string.mcp_approval_notification_title))
                .setContentText(getString(R.string.mcp_notification_pending, pendingCount))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
    }

    companion object {
        const val ACTION_START = "com.autopanel.feature.mcp.action.START"
        const val ACTION_STOP = "com.autopanel.feature.mcp.action.STOP"
        private const val CHANNEL_ID = "azureql_mcp_service"
        private const val APPROVAL_CHANNEL_ID = "azureql_mcp_approval"
        private const val NOTIFICATION_ID = 0x4D43
        private const val APPROVAL_NOTIFICATION_ID = 0x4D44
        private const val SHUTDOWN_TIMEOUT_MS = 5_000L
    }
}
