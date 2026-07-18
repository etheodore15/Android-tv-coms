package family.tvlink.tv

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import family.tvlink.core.Config
import family.tvlink.core.ConnectionState
import family.tvlink.core.FamilyCrypto
import family.tvlink.core.Message
import family.tvlink.core.MessageLog
import family.tvlink.core.RealtimeBus
import family.tvlink.core.SettingsStore
import kotlinx.coroutines.flow.first
import family.tvlink.core.ui.FamilyCodeEditor
import family.tvlink.core.ui.FamilyTvLinkTheme
import family.tvlink.core.ui.UpdateSection
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single settings/status screen for the TV: connection status, sender name,
 * overlay-permission helper, and a "Test overlay" button.
 */
class TvActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var overlayGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TvMessageService.start(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // First launch: mint this family's link code so the screen can display
        // it immediately for the phones to enter.
        lifecycleScope.launch {
            if (SettingsStore.familyCode(this@TvActivity).first().isBlank()) {
                SettingsStore.setFamilyCode(this@TvActivity, FamilyCrypto.generateLinkCode())
            }
        }

        setContent {
            FamilyTvLinkTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TvScreen(
                        overlayGranted = overlayGranted,
                        onRequestOverlayPermission = ::openOverlayPermissionScreen,
                        onTestOverlay = { TvMessageService.start(this, TvMessageService.ACTION_TEST_OVERLAY) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = Settings.canDrawOverlays(this)
    }

    private fun openOverlayPermissionScreen() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }
}

/**
 * Displays this family's link code for phones to enter, with a regenerate
 * button (unlinks everything until devices enter the new code) and a manual
 * entry field for joining another TV's existing family.
 */
@Composable
private fun LinkCodeCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val familyCode by SettingsStore.familyCode(context).collectAsState(initial = "")
    var showManualEntry by remember { mutableStateOf(false) }

    Card {
        Column(
            Modifier.padding(24.dp).width(560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Link code", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (familyCode.isBlank()) "…" else FamilyCrypto.formatForDisplay(familyCode),
                fontSize = 44.sp,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "On each phone, open FamilyTV Link and enter this code to link it to this TV.",
                fontSize = 18.sp,
                lineHeight = 26.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    scope.launch {
                        SettingsStore.setFamilyCode(context, FamilyCrypto.generateLinkCode())
                    }
                }) {
                    Text("Generate new code")
                }
                Button(onClick = { showManualEntry = !showManualEntry }) {
                    Text("Enter a code instead")
                }
            }
            Text(
                "Generating a new code unlinks every device until it enters the new one.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showManualEntry) {
                FamilyCodeEditor(
                    label = "Code from another TV (to join its family)",
                    warningWhenUnset = null,
                    onSaved = { code ->
                        scope.launch {
                            val name = SettingsStore
                                .senderName(context, Config.DEFAULT_TV_SENDER_NAME).first()
                            runCatching {
                                RealtimeBus.publish(
                                    Config.CHANNEL_TO_TV,
                                    code,
                                    Message(
                                        from = name,
                                        text = "linked",
                                        ts = System.currentTimeMillis(),
                                        kind = Message.KIND_HELLO,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TvScreen(
    overlayGranted: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onTestOverlay: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val connectionState by RealtimeBus.connectionState
        .collectAsState(initial = ConnectionState.DISCONNECTED)
    val senderName by SettingsStore.senderName(context, Config.DEFAULT_TV_SENDER_NAME)
        .collectAsState(initial = Config.DEFAULT_TV_SENDER_NAME)
    val lastReceived by MessageLog.lastReceived.collectAsState()
    var nameField by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("FamilyTV Link", style = MaterialTheme.typography.headlineLarge)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Connection: ", fontSize = 22.sp)
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.CONNECTING -> "Connecting…"
                    ConnectionState.DISCONNECTED -> "Disconnected — retrying"
                },
                fontSize = 22.sp,
                color = when (connectionState) {
                    ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                    ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
                },
            )
        }

        lastReceived?.let { msg ->
            Text(
                "Last message received: ${msg.from}: \"${msg.text}\" at " +
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.ts)),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LinkCodeCard()

        OutlinedTextField(
            value = nameField ?: senderName,
            onValueChange = { value ->
                nameField = value
                scope.launch { SettingsStore.setSenderName(context, value) }
            },
            label = { Text("TV name (send target on the phone; shown on replies)") },
            singleLine = true,
            modifier = Modifier.width(420.dp),
        )

        if (!overlayGranted) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Overlay permission needed",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "To pop messages on top of what's playing, allow \"Display over other apps\" " +
                            "for FamilyTV Link. Press the button below, find FamilyTV Link in the list " +
                            "and switch it on, then press BACK to return. If your TV has no such screen, " +
                            "run: adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow. " +
                            "Until granted, messages arrive as notifications instead.",
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                    )
                    Button(onClick = onRequestOverlayPermission) {
                        Text("Open permission settings")
                    }
                }
            }
        } else {
            Text("Overlay permission granted ✓", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        }

        Button(onClick = onTestOverlay) {
            Text("Test overlay", fontSize = 20.sp)
        }

        UpdateSection()
    }
}
