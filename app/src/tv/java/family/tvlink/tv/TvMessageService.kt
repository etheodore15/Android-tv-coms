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
import android.widget.Toast
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
            SettingsStore.familyCode(this@TvMessageService)
                .distinctUntilChanged()
                .collectLatest { code ->
                    if (code.isBlank()) return@collectLatest
                    RealtimeBus.subscribe(Config.CHANNEL_TO_TV, code).collect { message ->
                        when {
                            message.kind == Message.KIND_HELLO -> onDeviceLinked(message)
                            message.kind != null -> Unit
                            isAddressedToThisTv(message) ->
                                withContext(Dispatchers.Main) { showMessage(message) }
                        }
                    }
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

    /**
     * A device just entered our link code: confirm on the TV screen and send
     * an ack so the phone shows "Linked" too — the pairing handshake.
     */
    private suspend fun onDeviceLinked(hello: Message) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@TvMessageService, "${hello.from} linked ✓", Toast.LENGTH_LONG).show()
        }
        val code = SettingsStore.familyCode(this).first()
        if (code.isBlank()) return
        val name = SettingsStore.senderName(this, Config.DEFAULT_TV_SENDER_NAME).first()
        runCatching {
            RealtimeBus.publish(
                Config.CHANNEL_TO_PHONE,
                code,
                Message(from = name, text = "Linked ✓", ts = System.currentTimeMillis(), kind = Message.KIND_ACK),
            )
        }
    }

    /** A message with no target is for every TV; otherwise match this TV's name. */
    private suspend fun isAddressedToThisTv(message: Message): Boolean {
        val target = message.to?.trim().takeUnless { it.isNullOrEmpty() } ?: return true
        val myName = SettingsStore.senderName(this, Config.DEFAULT_TV_SENDER_NAME).first()
        return target.equals(myName.trim(), ignoreCase = true)
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
            val code = SettingsStore.familyCode(this@TvMessageService).first()
            if (code.isBlank()) return@launch
            val name = SettingsStore.senderName(this@TvMessageService, Config.DEFAULT_TV_SENDER_NAME).first()
            runCatching {
                RealtimeBus.publish(
                    Config.CHANNEL_TO_PHONE,
                    code,
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
