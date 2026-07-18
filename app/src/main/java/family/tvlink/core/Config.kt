package family.tvlink.core

/**
 * All family-editable settings live in this one file.
 *
 * Fill in [SUPABASE_URL] and [SUPABASE_ANON_KEY] from your Supabase project:
 * Dashboard -> Settings -> API -> "Project URL" and "anon public" key.
 * The anon key can be rotated from the dashboard if an APK ever leaves the family.
 */
object Config {
    const val SUPABASE_URL = "https://YOUR-PROJECT-REF.supabase.co"
    const val SUPABASE_ANON_KEY = "YOUR-ANON-PUBLIC-KEY"

    /** Broadcast channel the phone publishes to and the TV listens on. */
    const val CHANNEL_TO_TV = "to-tv"

    /** Broadcast channel the TV publishes replies to and the phone listens on. */
    const val CHANNEL_TO_PHONE = "to-phone"

    /** Broadcast event name used for all messages. */
    const val BROADCAST_EVENT = "message"

    /** Canned replies shown on the TV overlay, in order. */
    val CANNED_REPLIES = listOf("OK", "5 mins", "Coming", "No")

    /** Preset message buttons on the phone, in order. */
    val PRESET_MESSAGES = listOf(
        "Dinner's ready",
        "Come here please",
        "Turn it down",
        "Bedtime",
        "Phone call for you",
        "All good?",
    )

    /** Overlay auto-dismisses after this long with no D-pad interaction. */
    const val OVERLAY_TIMEOUT_MS = 45_000L

    const val DEFAULT_PHONE_SENDER_NAME = "Phone"
    const val DEFAULT_TV_SENDER_NAME = "TV"
}
