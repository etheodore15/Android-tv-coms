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

    /**
     * Where the GitHub Pages install page lives; the apps check
     * `version.json` here and offer one-tap in-app updates.
     */
    const val UPDATE_BASE_URL = "https://etheodore15.github.io/Android-tv-coms/"

    /** Broadcast channel the phone publishes to and the TV listens on. */
    const val CHANNEL_TO_TV = "to-tv"

    /** Broadcast channel the TV publishes replies to and the phone listens on. */
    const val CHANNEL_TO_PHONE = "to-phone"

    /** Broadcast event name used for all messages. */
    const val BROADCAST_EVENT = "message"

    /** Canned replies shown on the TV overlay, in order. */
    val CANNED_REPLIES = listOf("OK", "5 mins", "Coming", "No")

    /**
     * The family's TVs, shown as send targets on the phone. Each TV's
     * "Sender name" (on its settings screen) must match one of these entries
     * for addressed messages to reach it; messages sent to "All TVs" reach
     * every TV regardless. With a single TV the default name "TV" works
     * as-is — just leave this list as it is.
     */
    val TV_NAMES = listOf("TV")

    /** Phone-side label for the send-to-every-TV option. */
    const val ALL_TVS_LABEL = "All TVs"

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
