package family.tvlink.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Button with an unmissable D-pad focus state for the 10-foot UI: the active
 * button flips to a bright container with dark bold text and grows slightly,
 * so it's always obvious which one OK will press. On touch screens the focus
 * state simply never shows. `outlined` renders the resting state as a
 * lower-emphasis outlined button; the focused state is identical.
 */
@Composable
fun FocusHighlightButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    outlined: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Button(
        onClick = onClick,
        modifier = modifier.scale(if (focused) 1.05f else 1f),
        interactionSource = interactionSource,
        colors = when {
            focused -> ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF101418),
            )
            outlined -> ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            else -> ButtonDefaults.buttonColors()
        },
        border = if (outlined && !focused) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else {
            null
        },
    ) {
        Text(
            text,
            maxLines = 1,
            fontSize = fontSize,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
