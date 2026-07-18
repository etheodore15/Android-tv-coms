package family.tvlink.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Last chat message received by this device, for the settings/status screen —
 * proves receipt even when the on-screen display path (overlay) is blocked.
 * In-memory only; messages remain ephemeral.
 */
object MessageLog {
    val lastReceived = MutableStateFlow<Message?>(null)

    /** How the last message was displayed ("overlay ✓" or a fallback reason). */
    val lastDisplayMethod = MutableStateFlow<String?>(null)
}
