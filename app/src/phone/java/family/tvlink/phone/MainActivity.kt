package family.tvlink.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import family.tvlink.core.Config
import family.tvlink.core.ConnectionState
import family.tvlink.core.Message
import family.tvlink.core.RealtimeBus
import family.tvlink.core.SettingsStore
import family.tvlink.core.ui.FamilyCodeEditor
import family.tvlink.core.ui.FamilyTvLinkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ContextCompat.startForegroundService(this, Intent(this, PhoneListenerService::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            FamilyTvLinkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhoneScreen(
                        isIgnoringBatteryOptimizations = ::isIgnoringBatteryOptimizations,
                        onRequestBatteryExemption = ::requestBatteryExemption,
                    )
                }
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun requestBatteryExemption() {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        if (direct.resolveActivity(packageManager) != null) {
            startActivity(direct)
        } else {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

@Composable
private fun PhoneScreen(
    isIgnoringBatteryOptimizations: () -> Boolean,
    onRequestBatteryExemption: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val connectionState by RealtimeBus.connectionState
        .collectAsState(initial = ConnectionState.DISCONNECTED)
    val senderName by SettingsStore.senderName(context, Config.DEFAULT_PHONE_SENDER_NAME)
        .collectAsState(initial = Config.DEFAULT_PHONE_SENDER_NAME)
    val targetTv by SettingsStore.targetTv(context).collectAsState(initial = null)
    val familyCode by SettingsStore.familyCode(context).collectAsState(initial = "")

    var nameField by remember { mutableStateOf<String?>(null) }
    var freeText by remember { mutableStateOf("") }
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations()) }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (familyCode.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Set the family code first") }
            return
        }
        // Guard against a stale selection after Config.TV_NAMES is edited.
        val target = targetTv?.takeIf { it in Config.TV_NAMES }
        scope.launch {
            val result = runCatching {
                RealtimeBus.publish(
                    Config.CHANNEL_TO_TV,
                    familyCode,
                    Message(
                        from = senderName,
                        text = trimmed,
                        ts = System.currentTimeMillis(),
                        to = target,
                    ),
                )
            }
            snackbarHostState.showSnackbar(
                if (result.isSuccess) {
                    "Sent to ${target ?: Config.ALL_TVS_LABEL} ✓"
                } else {
                    "Send failed — check connection"
                },
            )
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionBanner(connectionState)

            if (!batteryExempt) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "To keep receiving TV replies, exempt this app from battery optimisation.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRequestBatteryExemption) { Text("Open settings") }
                            OutlinedButton(onClick = { batteryExempt = isIgnoringBatteryOptimizations() }) {
                                Text("Done")
                            }
                        }
                    }
                }
            }

            FamilyCodeEditor(onSaved = { code ->
                scope.launch {
                    val result = runCatching {
                        RealtimeBus.publish(
                            Config.CHANNEL_TO_TV,
                            code,
                            Message(
                                from = senderName,
                                text = "linked",
                                ts = System.currentTimeMillis(),
                                kind = Message.KIND_HELLO,
                            ),
                        )
                    }
                    snackbarHostState.showSnackbar(
                        if (result.isSuccess) {
                            "Code saved — look for the confirmation on the TV"
                        } else {
                            "Code saved, but the TV couldn't be reached — check connection"
                        },
                    )
                }
            })

            OutlinedTextField(
                value = nameField ?: senderName,
                onValueChange = { value ->
                    nameField = value
                    scope.launch { SettingsStore.setSenderName(context, value) }
                },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (Config.TV_NAMES.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = targetTv == null,
                        onClick = { scope.launch { SettingsStore.setTargetTv(context, null) } },
                        label = { Text(Config.ALL_TVS_LABEL) },
                    )
                    Config.TV_NAMES.forEach { tv ->
                        FilterChip(
                            selected = targetTv == tv,
                            onClick = { scope.launch { SettingsStore.setTargetTv(context, tv) } },
                            label = { Text(tv) },
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(Config.PRESET_MESSAGES) { preset ->
                    Button(
                        onClick = { send(preset) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Text(preset, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = freeText,
                    onValueChange = { freeText = it },
                    label = { Text("Message") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        send(freeText)
                        freeText = ""
                    },
                    enabled = freeText.isNotBlank(),
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun ConnectionBanner(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.CONNECTED ->
            "Connected" to MaterialTheme.colorScheme.primaryContainer
        ConnectionState.CONNECTING ->
            "Connecting…" to MaterialTheme.colorScheme.secondaryContainer
        ConnectionState.DISCONNECTED ->
            "Offline — reconnecting. Sends may fail." to MaterialTheme.colorScheme.errorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Text(
            text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
