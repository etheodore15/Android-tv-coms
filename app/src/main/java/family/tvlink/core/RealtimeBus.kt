package family.tvlink.core

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class ConnectionState { CONNECTED, CONNECTING, DISCONNECTED }

/**
 * Thin wrapper around supabase-kt Realtime Broadcast.
 *
 * All transport concerns live here so a future switch to table-backed history
 * (see build brief section 9) stays a contained change.
 */
object RealtimeBus {

    private val client: SupabaseClient by lazy {
        createSupabaseClient(Config.SUPABASE_URL, Config.SUPABASE_ANON_KEY) {
            install(Realtime)
        }
    }

    private val channels = mutableMapOf<String, RealtimeChannel>()
    private val channelMutex = Mutex()

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
     * Publish a message on a Broadcast channel.
     *
     * If the websocket is not currently joined to the channel, supabase-kt
     * falls back to the Broadcast HTTP endpoint, so sends still work while
     * the socket is down or the channel is publish-only (phone -> to-tv).
     */
    suspend fun publish(channelName: String, message: Message) {
        val ch = channelFor(channelName)
        ch.broadcast(Config.BROADCAST_EVENT, Json.encodeToJsonElement(message).jsonObject)
    }

    /**
     * Subscribe to a Broadcast channel and emit incoming messages.
     *
     * Re-subscribes the channel whenever the websocket reconnects, so a WiFi
     * drop never leaves the collector silently detached.
     */
    fun subscribe(channelName: String): Flow<Message> = channelFlow {
        val ch = channelFor(channelName)
        val messages = ch.broadcastFlow<Message>(Config.BROADCAST_EVENT)
        launch { messages.collect { send(it) } }
        launch {
            client.realtime.status.collect { status ->
                if (status == Realtime.Status.CONNECTED &&
                    ch.status.value == RealtimeChannel.Status.UNSUBSCRIBED
                ) {
                    runCatching { ch.subscribe() }
                }
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
