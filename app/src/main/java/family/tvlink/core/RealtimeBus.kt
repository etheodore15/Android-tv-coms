package family.tvlink.core

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class ConnectionState { CONNECTED, CONNECTING, DISCONNECTED }

/**
 * Thin wrapper around supabase-kt Realtime Broadcast.
 *
 * All transport concerns live here so a future switch to table-backed history
 * (see build brief section 9) stays a contained change.
 *
 * Every publish/subscribe takes the family code: channel names are derived
 * from it and payloads are AES-GCM encrypted with it (see [FamilyCrypto]),
 * so holding the APK/anon key alone is not enough to reach the family.
 */
object RealtimeBus {

    private val client: SupabaseClient by lazy {
        createSupabaseClient(Config.SUPABASE_URL, Config.SUPABASE_ANON_KEY) {
            install(Realtime)
        }
    }

    private val channels = mutableMapOf<String, RealtimeChannel>()
    private val channelMutex = Mutex()
    private val busScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Connection state of the underlying Realtime websocket, for status UI. */
    val connectionState: Flow<ConnectionState>
        get() = client.realtime.status.map { status ->
            when (status) {
                Realtime.Status.CONNECTED -> ConnectionState.CONNECTED
                Realtime.Status.CONNECTING -> ConnectionState.CONNECTING
                Realtime.Status.DISCONNECTED -> ConnectionState.DISCONNECTED
            }
        }

    private suspend fun channelFor(name: String): RealtimeChannel = channelMutex.withLock {
        channels.getOrPut(name) { client.channel(name) }
    }

    /**
     * Publish a message on a Broadcast channel derived from [familyCode].
     *
     * If the websocket is not currently joined to the channel, supabase-kt
     * falls back to the Broadcast HTTP endpoint, so sends still work while
     * the socket is down or the channel is publish-only (phone -> to-tv).
     */
    suspend fun publish(channelBase: String, familyCode: String, message: Message) {
        withContext(Dispatchers.Default) {
            val key = FamilyCrypto.deriveKey(familyCode)
            val ch = channelFor(FamilyCrypto.channelName(channelBase, key))
            ch.broadcast(Config.BROADCAST_EVENT, FamilyCrypto.encryptMessage(key, message))
        }
    }

    /**
     * Subscribe to the Broadcast channel derived from [familyCode] and emit
     * incoming messages; payloads that don't decrypt with the code are
     * dropped. Re-subscribes whenever the websocket reconnects, and leaves
     * the channel when the collector is cancelled (e.g. on a code change).
     */
    fun subscribe(channelBase: String, familyCode: String): Flow<Message> = channelFlow {
        val key = withContext(Dispatchers.Default) { FamilyCrypto.deriveKey(familyCode) }
        val channelName = FamilyCrypto.channelName(channelBase, key)
        val ch = channelFor(channelName)
        val payloads = ch.broadcastFlow<JsonObject>(Config.BROADCAST_EVENT)
        launch {
            payloads.collect { payload ->
                FamilyCrypto.decryptMessage(key, payload)?.let { send(it) }
            }
        }
        launch {
            client.realtime.status.collect { status ->
                if (status == Realtime.Status.CONNECTED &&
                    ch.status.value == RealtimeChannel.Status.UNSUBSCRIBED
                ) {
                    runCatching { ch.subscribe() }
                }
            }
        }
        awaitClose {
            busScope.launch {
                channelMutex.withLock { channels.remove(channelName) }
                runCatching { ch.unsubscribe() }
                runCatching { client.realtime.removeChannel(ch) }
            }
        }
    }

    /**
     * Keep the websocket alive forever: connect, wait for a disconnect, then
     * retry with exponential backoff (1 s doubling up to 60 s, reset on a
     * successful connection). Never throws; call from a service scope.
     */
    suspend fun maintainConnection() {
        var backoff: Duration = 1.seconds
        while (currentCoroutineContext().isActive) {
            runCatching { client.realtime.connect() }
            if (client.realtime.status.value == Realtime.Status.CONNECTED) {
                backoff = 1.seconds
                client.realtime.status.first { it == Realtime.Status.DISCONNECTED }
            }
            delay(backoff)
            backoff = minOf(backoff * 2, 60.seconds)
        }
    }
}
