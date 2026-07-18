package family.tvlink.phone

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import family.tvlink.core.Config
import family.tvlink.core.Notifications
import family.tvlink.core.RealtimeBus
import family.tvlink.core.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the Realtime websocket alive and turns
 * incoming replies on the to-phone channel into notifications.
 */
class PhoneListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        scope.launch { RealtimeBus.maintainConnection() }
        scope.launch {
            SettingsStore.familyCode(this@PhoneListenerService)
                .distinctUntilChanged()
                .collectLatest { code ->
                    if (code.isBlank()) return@collectLatest
                    RealtimeBus.subscribe(Config.CHANNEL_TO_PHONE, code).collect { message ->
                        Notifications.showMessageNotification(this@PhoneListenerService, message)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
