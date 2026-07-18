package family.tvlink.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Last chat message received by this device, for the settings/status screen —
 * proves receipt even when the on-screen display path (overlay) is blocked.
 * In-memory only; messages remain ephemeral.
 */
object MessageLog {
    val lastReceived = MutableStateFlow<Message?>(null)
}
