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
)
