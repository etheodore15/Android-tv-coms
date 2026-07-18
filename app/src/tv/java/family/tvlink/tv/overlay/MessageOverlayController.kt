package family.tvlink.tv.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import family.tvlink.core.Config
import family.tvlink.core.Message
import family.tvlink.core.ui.FamilyTvLinkTheme
import family.tvlink.core.ui.FocusHighlightButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Draws the incoming-message overlay on top of whatever is playing, using a
 * TYPE_APPLICATION_OVERLAY window. Fully D-pad driven: the window is focusable,
 * LEFT/RIGHT move between replies, CENTER selects, BACK dismisses, and the
 * overlay auto-dismisses after [Config.OVERLAY_TIMEOUT_MS] with no interaction.
 *
 * Must be used from the main thread.
 */
class MessageOverlayController(
    private val context: Context,
    private val onReply: (String) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var container: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var timeoutJob: Job? = null

    fun show(message: Message) {
        dismiss()

        val owner = OverlayLifecycleOwner().also { lifecycleOwner = it }
        owner.onCreate()

        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                restartTimeout()
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) dismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        owner.attachTo(root)

        val composeView = ComposeView(context).apply {
            setContent {
                FamilyTvLinkTheme(darkTheme = true) {
                    OverlayContent(
                        message = message,
                        onReply = { reply ->
                            dismiss()
                            onReply(reply)
                        },
                        onDismiss = { dismiss() },
                    )
                }
            }
        }
        root.addView(composeView)
        container = root

        val screenWidth = context.resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            (screenWidth * 0.4).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (context.resources.displayMetrics.density * 32).toInt()
        }

        windowManager.addView(root, params)
        restartTimeout()
    }

    fun dismiss() {
        timeoutJob?.cancel()
        timeoutJob = null
        container?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        container = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    private fun restartTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(Config.OVERLAY_TIMEOUT_MS)
            dismiss()
        }
    }
}

@Composable
private fun OverlayContent(
    message: Message,
    onReply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstReplyFocus = remember { FocusRequester() }

    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message.from,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message.text,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Config.CANNED_REPLIES.forEachIndexed { index, reply ->
                    FocusHighlightButton(
                        text = reply,
                        onClick = { onReply(reply) },
                        modifier = if (index == 0) {
                            Modifier.weight(1f).focusRequester(firstReplyFocus)
                        } else {
                            Modifier.weight(1f)
                        },
                    )
                }
            }
            FocusHighlightButton(
                text = "Dismiss",
                onClick = onDismiss,
                outlined = true,
            )
            Text(
                "◀ ▶ choose · OK sends · BACK closes",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    LaunchedEffect(Unit) {
        firstReplyFocus.requestFocus()
    }
}
