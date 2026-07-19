package family.tvlink.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import family.tvlink.core.SettingsStore
import kotlinx.coroutines.launch

/**
 * Collapsible "TV toolbox": fixes the TV's overlay permission from the phone
 * via network ADB, for TVs whose settings menus hide the permission screen.
 */
@Composable
fun TvFixerSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "TV toolbox ▲" else "TV toolbox (fix overlay permission) ▼")
        }
        if (!expanded) return@Column

        val savedIp by SettingsStore.tvIp(context).collectAsState(initial = "")
        var ipField by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("") }

        Card {
            Column(
                Modifier.padding(12.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Grants the TV's \"Display over other apps\" permission over your WiFi — " +
                        "no computer needed. One-time prep on the TV: Settings → System → About, " +
                        "click \"Android TV OS build\" 7 times; then System → Developer options → " +
                        "enable USB/Network debugging. Find the TV's IP under Settings → Network. " +
                        "When you press the button, accept the \"Allow debugging?\" prompt on the TV.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = ipField ?: savedIp,
                        onValueChange = { ipField = it },
                        label = { Text("TV IP address") },
                        placeholder = { Text("192.168.1.23") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = !busy && (ipField ?: savedIp).isNotBlank(),
                        onClick = {
                            val ip = (ipField ?: savedIp).trim()
                            scope.launch {
                                busy = true
                                status = "Connecting — accept the prompt on the TV if it appears…"
                                SettingsStore.setTvIp(context, ip)
                                val result = runCatching {
                                    TvAdbFixer.grantOverlayPermission(context, ip)
                                }
                                busy = false
                                status = result.fold(
                                    onSuccess = { "TV reports: $it — reopen FamilyTV Link on the TV and press Test overlay." },
                                    onFailure = { e ->
                                        "Failed: ${e.message ?: e::class.simpleName}. Check the TV's IP, " +
                                            "that network debugging is on, and that you accepted the TV prompt."
                                    },
                                )
                            }
                        },
                    ) {
                        Text(if (busy) "Working…" else "Fix TV overlay")
                    }
                }
                if (status.isNotEmpty()) {
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
