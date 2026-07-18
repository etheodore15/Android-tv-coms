package family.tvlink.core.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import family.tvlink.BuildConfig
import family.tvlink.core.UpdateChecker
import kotlinx.coroutines.launch

/**
 * "App version X · Update to Y" row. Checks the install site silently on
 * open; when a newer build is published, one tap downloads it and opens the
 * system installer (after a one-time "install unknown apps" grant).
 */
@Composable
fun UpdateSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<UpdateChecker.VersionInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun check(silent: Boolean) {
        scope.launch {
            busy = true
            val latest = UpdateChecker.fetchLatest()
            busy = false
            when {
                latest == null -> if (!silent) status = "Couldn't check for updates — offline?"
                UpdateChecker.isNewer(latest) -> { available = latest; status = "" }
                else -> { available = null; if (!silent) status = "Up to date" }
            }
        }
    }

    LaunchedEffect(Unit) { check(silent = true) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("App version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            val update = available
            if (update == null) {
                OutlinedButton(onClick = { check(silent = false) }, enabled = !busy) {
                    Text(if (busy) "Checking…" else "Check for updates")
                }
            } else {
                Button(
                    onClick = {
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            status = "Allow \"install unknown apps\" for FamilyTV Link, then tap Update again."
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        } else {
                            scope.launch {
                                busy = true
                                status = "Downloading update…"
                                val apk = UpdateChecker.downloadApk(context)
                                busy = false
                                if (apk != null) {
                                    status = ""
                                    context.startActivity(UpdateChecker.installIntent(context, apk))
                                } else {
                                    status = "Download failed — try again"
                                }
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text(if (busy) "Downloading…" else "Update to ${update.versionName}")
                }
            }
        }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
