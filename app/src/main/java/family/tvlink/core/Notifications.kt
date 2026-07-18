package family.tvlink.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import family.tvlink.R

object Notifications {
    const val CHANNEL_SERVICE = "service"
    const val CHANNEL_MESSAGES = "messages"

    const val SERVICE_NOTIFICATION_ID = 1
    private const val MESSAGE_NOTIFICATION_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                context.getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    fun serviceNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_service_text))
            .setOngoing(true)
            .build()

    /** High-priority heads-up notification for an incoming [Message]. */
    fun showMessageNotification(context: Context, message: Message) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.from)
            .setContentText(message.text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(MESSAGE_NOTIFICATION_ID, notification)
    }
}
