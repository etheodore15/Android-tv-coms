package family.tvlink.phone

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Grants the TV app's overlay permission over the network from the phone,
 * using the ADB protocol directly (no computer needed). Requires Developer
 * options + network debugging enabled on the TV; the first connection pops
 * an "Allow debugging?" prompt on the TV that must be accepted once.
 */
object TvAdbFixer {

    private const val TV_PACKAGE = "family.tvlink.tv"
    private const val ADB_PORT = 5555

    /** Persistent ADB identity so the TV's "always allow" choice sticks. */
    private fun keyPair(context: Context): AdbKeyPair {
        val privateKey = File(context.filesDir, "adbkey")
        val publicKey = File(context.filesDir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }

    /**
     * Connects to the TV and runs
     * `appops set family.tvlink.tv SYSTEM_ALERT_WINDOW allow`.
     * Returns the TV's reported permission state; throws with a readable
     * message on failure.
     */
    suspend fun grantOverlayPermission(context: Context, tvIp: String): String =
        withContext(Dispatchers.IO) {
            Dadb.create(tvIp.trim(), ADB_PORT, keyPair(context)).use { dadb ->
                val set = dadb.shell("appops set $TV_PACKAGE SYSTEM_ALERT_WINDOW allow")
                if (set.exitCode != 0) {
                    throw IOException(
                        set.allOutput.trim().ifBlank { "appops failed (exit ${set.exitCode})" }
                    )
                }
                val check = dadb.shell("appops get $TV_PACKAGE SYSTEM_ALERT_WINDOW")
                check.output.trim().ifBlank { "granted" }
            }
        }
}
