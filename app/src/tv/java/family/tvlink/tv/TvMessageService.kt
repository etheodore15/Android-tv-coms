package family.tvlink.tv

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import family.tvlink.core.Config
import family.tvlink.core.Message
import family.tvlink.core.Notifications
import family.tvlink.core.RealtimeBus
import family.tvlink.core.SettingsStore
import family.tvlink.tv.overlay.MessageOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service on the TV: keeps the Realtime websocket alive, shows the
 * overlay for incoming messages on the to-tv channel, and broadcasts the
 * chosen canned reply on the to-phone channel.
 */
class TvMessageService : Service() {

    companion object {
        const val ACTION_TEST_OVERLAY = "family.tvlink.tv.action.TEST_OVERLAY"

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, TvMessageService::class.java).apply {
                if (action != null) this.action = action
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var overlayController: MessageOverlayController

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            Notifications.SERVICE_NOTIFICATION_ID,
            Notifications.serviceNotification(this),
            type,
        )

        overlayController = MessageOverlayController(this, onReply = ::sendReply)

        scope.launch { RealtimeBus.maintainConnection() }
        scope.launch {
            RealtimeBus.subscribe(Config.CHANNEL_TO_TV).collect { message ->
                withContext(Dispatchers.Main) { showMessage(message) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST_OVERLAY) {
            scope.launch(Dispatchers.Main) {
                showMessage(
                    Message(
                        from = "Phone",
                        text = "Test message — dinner's ready!",
                        ts = System.currentTimeMillis(),
                    )
                )
            }
        }
        return START_STICKY
    }

    /** Overlay when permitted; high-priority notification fallback otherwise. */
    private fun showMessage(message: Message) {
        if (Settings.canDrawOverlays(this)) {
            overlayController.show(message)
        } else {
            Notifications.showMessageNotification(this, message)
        }
    }

    private fun sendReply(reply: String) {
        scope.launch {
            val name = SettingsStore.senderName(this@TvMessageService, Config.DEFAULT_TV_SENDER_NAME).first()
            runCatching {
                RealtimeBus.publish(
                    Config.CHANNEL_TO_PHONE,
                    Message(from = name, text = reply, ts = System.currentTimeMillis()),
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        Handler(Looper.getMainLooper()).post { overlayController.dismiss() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
