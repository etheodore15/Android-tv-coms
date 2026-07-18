package family.tvlink.core

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Clears a message notification when its "Got it" action (or body) is tapped. */
class NotificationAckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id != -1) {
            context.getSystemService(NotificationManager::class.java).cancel(id)
        }
    }

    companion object {
        const val EXTRA_ID = "notification_id"
    }
}
