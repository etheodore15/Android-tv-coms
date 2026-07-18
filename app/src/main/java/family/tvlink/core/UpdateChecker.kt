package family.tvlink.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import family.tvlink.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * In-app updates from the GitHub Pages install site. The CI workflow writes a
 * version.json next to the APKs; the app compares it with its own versionCode
 * and, when newer, downloads the right flavor's APK and hands it to the
 * system installer. Same signing key, so it always installs as an update.
 */
object UpdateChecker {

    @Serializable
    data class VersionInfo(val versionCode: Int, val versionName: String)

    private val http by lazy { HttpClient(OkHttp) }
    private val json = Json { ignoreUnknownKeys = true }

    private val apkFileName: String
        get() = if (BuildConfig.FLAVOR == "tv") "tv.apk" else "phone.apk"

    /** Latest published version, or null if the check failed (offline etc.). */
    suspend fun fetchLatest(): VersionInfo? = runCatching {
        // Cache-buster: GitHub Pages caches aggressively.
        val text = http
            .get(Config.UPDATE_BASE_URL + "version.json?ts=" + System.currentTimeMillis())
            .bodyAsText()
        json.decodeFromString<VersionInfo>(text)
    }.getOrNull()

    fun isNewer(info: VersionInfo): Boolean = info.versionCode > BuildConfig.VERSION_CODE

    /** Download this flavor's APK into the app cache; null on failure. */
    suspend fun downloadApk(context: Context): File? = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, apkFileName)
        val bytes: ByteArray = http.get(Config.UPDATE_BASE_URL + apkFileName).body()
        file.writeBytes(bytes)
        file
    }.getOrNull()

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
