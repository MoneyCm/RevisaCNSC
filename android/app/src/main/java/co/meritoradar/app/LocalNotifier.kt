package co.meritoradar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Local notification system for Mérito Radar.
 * Replaces FCM with direct Android notifications.
 */
class LocalNotifier(private val context: Context) {

    companion object {
        private const val CHANNEL_CRITICAL = "critical_alerts"
        private const val CHANNEL_IMPORTANT = "important_updates"
        private const val CHANNEL_INFO = "general_info"

        private const val NOTIFICATION_ID_BASE = 1000

        data class ChannelStatus(val channelId: String, val label: String, val blocked: Boolean)

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // Critical alerts channel
                val criticalChannel = NotificationChannel(
                    CHANNEL_CRITICAL,
                    "Alertas urgentes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alertas críticas como inscripciones abiertas, recaudos y cambios importantes"
                    enableVibration(true)
                    enableLights(true)
                }

                // Important updates channel
                val importantChannel = NotificationChannel(
                    CHANNEL_IMPORTANT,
                    "Actualizaciones importantes",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Actualizaciones importantes como citaciones y resultados"
                    enableVibration(true)
                }

                // General info channel
                val infoChannel = NotificationChannel(
                    CHANNEL_INFO,
                    "Información general",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Información general como noticias y documentos"
                }

                notificationManager.createNotificationChannel(criticalChannel)
                notificationManager.createNotificationChannel(importantChannel)
                notificationManager.createNotificationChannel(infoChannel)
            }
        }
    }

    fun channelForPriority(priority: String): String = when (priority) {
        "CRITICAL" -> CHANNEL_CRITICAL
        "IMPORTANT" -> CHANNEL_IMPORTANT
        else -> CHANNEL_INFO
    }

    /**
     * Public status of the three app channels for the diagnosis screen. A channel is
     * blocked when missing or set to IMPORTANCE_NONE (Android swallows the post).
     */
    fun channelStatuses(): List<ChannelStatus> = listOf(
        ChannelStatus(CHANNEL_CRITICAL, "Alertas urgentes", isChannelBlocked(CHANNEL_CRITICAL)),
        ChannelStatus(CHANNEL_IMPORTANT, "Actualizaciones importantes", isChannelBlocked(CHANNEL_IMPORTANT)),
        ChannelStatus(CHANNEL_INFO, "Información general", isChannelBlocked(CHANNEL_INFO))
    )

    /**
     * Check if notification permission is granted
     */
    fun hasNotificationPermission(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * A channel is deliverable only when it exists and is not disabled (IMPORTANCE_NONE).
     * A blocked channel swallows notify() without exception, so pending events must not
     * be dropped based on the global permission alone.
     */
    fun isChannelBlocked(channelId: String): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(channelId) ?: return true
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Show notification for CNSC event. Returns true only when the notification was
     * actually posted through the resolved channel; false when permission is missing,
     * the channel is blocked or the system refused the post. The caller decides whether
     * to keep the event in a durable outbox.
     */
    fun showEventNotification(
        eventId: String,
        processId: String,
        processName: String,
        eventType: String,
        title: String,
        message: String,
        priority: String = "INFO"
    ): Boolean {
        if (!hasNotificationPermission()) {
            return false
        }

        val channelId = channelForPriority(priority)
        if (isChannelBlocked(channelId)) {
            return false
        }

        // Create deep link intent
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.Builder().scheme("meritoradar").authority("process")
                .appendPath(processId).appendQueryParameter("eventId", eventId).build()
            putExtra("eventId", eventId)
            putExtra("processId", processId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(
                when (priority) {
                    "CRITICAL" -> NotificationCompat.PRIORITY_HIGH
                    "IMPORTANT" -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_LOW
                }
            )
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // The full event ID is the identity: Java hashes can collide for distinct events.
        return try {
            NotificationManagerCompat.from(context).notify(eventId, NOTIFICATION_ID_BASE, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Show test notification (for debugging)
     */
    fun showTestNotification(): Boolean {
        createNotificationChannels(context)
        if (!hasNotificationPermission()) return false
        if (isChannelBlocked(CHANNEL_INFO)) return false

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle("TEST - Mérito Radar")
            .setContentText("Esta es una notificación de prueba del sistema de vigilancia local.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Esta es una notificación de prueba del sistema de vigilancia local."))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(9999, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
