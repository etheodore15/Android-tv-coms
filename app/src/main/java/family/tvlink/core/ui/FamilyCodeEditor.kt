package family.tvlink.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import family.tvlink.core.SettingsStore
import kotlinx.coroutines.launch

/**
 * Link-code entry field with an explicit Save button (saving per keystroke
 * would churn the channel subscription). Codes are normalized on save, so
 * case and dashes don't matter when typing one in. Optionally shows a
 * warning card while no code is set — messaging is inactive until linked.
 */
@Composable
fun FamilyCodeEditor(
    modifier: Modifier = Modifier,
    label: String = "Link code (shown on the TV)",
    warningWhenUnset: String? =
        "Not linked yet — messaging is off. Open FamilyTV Link on the TV: it displays " +
            "a link code. Enter that code here to link this device. The code is never " +
            "part of the app download, so outsiders with the APK stay locked out.",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedCode by SettingsStore.familyCode(context).collectAsState(initial = "")
    var field by remember { mutableStateOf<String?>(null) }
    val dirty = field != null && field != savedCode

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (savedCode.isEmpty() && warningWhenUnset != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    warningWhenUnset,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = field ?: savedCode,
                onValueChange = { field = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val value = (field ?: savedCode).trim()
                    scope.launch { SettingsStore.setFamilyCode(context, value) }
                    field = null
                },
                enabled = dirty && !(field ?: "").isBlank(),
            ) {
                Text("Save")
            }
        }
    }
}
