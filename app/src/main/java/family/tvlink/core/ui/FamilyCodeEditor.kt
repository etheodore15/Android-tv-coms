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
 * Family-code field with an explicit Save button (saving per keystroke would
 * churn the channel subscription). Shows a warning card while no code is set:
 * messaging is inactive until every device carries the same code.
 */
@Composable
fun FamilyCodeEditor(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedCode by SettingsStore.familyCode(context).collectAsState(initial = "")
    var field by remember { mutableStateOf<String?>(null) }
    val dirty = field != null && field != savedCode

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (savedCode.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Set the family code to activate messaging. Pick a phrase together and " +
                        "enter exactly the same code on every phone and TV — it is never " +
                        "stored in the app download, so outsiders with the APK stay locked out.",
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
                label = { Text("Family code (same on every device)") },
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
