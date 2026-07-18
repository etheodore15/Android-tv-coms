package family.tvlink.core

import kotlinx.serialization.Serializable

/**
 * Wire format for both channels: { "from": "<sender name>", "text": "<message>", "ts": <epoch ms> }
 */
@Serializable
data class Message(
    val from: String,
    val text: String,
    val ts: Long,
)
