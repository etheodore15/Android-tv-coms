package family.tvlink.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import family.tvlink.R

object Notifications {
    const val CHANNEL_SERVICE = "service"
    const val CHANNEL_MESSAGES = "messages"

    const val SERVICE_NOTIFICATION_ID = 1

    // Every message gets its own notification so each one needs its own
    // acknowledgment; starts above the fixed service-notification id.
    private val nextMessageNotificationId = java.util.concurrent.atomic.AtomicInteger(100)

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

    /**
     * High-priority heads-up notification for an incoming [Message] that
     * stays pinned until explicitly acknowledged: the heads-up banner timing
     * is OS-controlled, but the notification itself is ongoing (not
     * swipe-dismissable before Android 14) and only clears via its
     * "Got it" action or a tap on the body.
     */
    fun showMessageNotification(context: Context, message: Message) {
        val id = nextMessageNotificationId.getAndIncrement()
        val ackPending = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, NotificationAckReceiver::class.java)
                .putExtra(NotificationAckReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.from)
            .setContentText(message.text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(ackPending)
            .addAction(0, "Got it", ackPending)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
