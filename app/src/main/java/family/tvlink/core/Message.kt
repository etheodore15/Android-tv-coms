package family.tvlink.core

import kotlinx.serialization.Serializable

/**
 * Wire format for both channels: { "from": "<sender name>", "text": "<message>", "ts": <epoch ms> }
 *
 * [to] optionally addresses a specific TV by name on the to-tv channel;
 * null (or absent, for payloads from older builds) means every TV shows it.
 */
@Serializable
data class Message(
    val from: String,
    val text: String,
    val ts: Long,
    val to: String? = null,
    /** null = normal chat message; see KIND_* for the pairing handshake. */
    val kind: String? = null,
) {
    companion object {
        /** Sent on to-tv right after a device saves the link code; TVs confirm on screen. */
        const val KIND_HELLO = "hello"

        /** TV's response to a hello, sent on to-phone; phones surface it as "Linked". */
        const val KIND_ACK = "ack"
    }
}
