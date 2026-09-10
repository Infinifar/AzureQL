package com.autopanel.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autopanel.app.MainActivity
import com.autopanel.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class QingLongMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = QingLongPushPayload.from(message.data) ?: return
        val channelId = channelId(payload.serverId)
        createChannel(channelId, payload.serverName)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SERVER_ID, payload.serverId)
            putExtra(EXTRA_EVENT_ID, payload.eventId)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            payload.eventId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val publicVersion = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.qinglong_notification_private_title))
            .setContentText(payload.title)
            .build()

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setGroup("qinglong:${payload.serverId}")
        if (payload.body.isNotEmpty()) {
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
        }
        val notification = notificationBuilder.build()

        runCatching {
            NotificationManagerCompat.from(this).notify(payload.eventId.hashCode(), notification)
        }
    }

    private fun createChannel(channelId: String, serverName: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "${getString(R.string.qinglong_notification_channel)} · $serverName",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.qinglong_notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun channelId(serverId: String): String =
        "qinglong_${serverId.hashCode().toUInt().toString(16)}"

    companion object {
        const val EXTRA_SERVER_ID = "qinglong_server_id"
        const val EXTRA_EVENT_ID = "qinglong_event_id"
    }
}
