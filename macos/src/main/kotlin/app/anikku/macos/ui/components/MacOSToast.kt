package app.anikku.macos.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Duration constants matching Android Toast conventions.
 */
enum class ToastDuration {
    /** ~2 seconds */
    SHORT,
    /** ~4 seconds */
    LONG,
}

/**
 * Internal data for a single toast message.
 */
internal data class ToastMessage(
    val id: Long,
    val text: String,
    val duration: ToastDuration,
)

/**
 * State holder for the toast host.
 *
 * Call [show] from any composable to display a brief message.
 *
 * Usage:
 * ```kotlin
 * val toastHost = LocalToastHost.current
 * toastHost.show("Settings saved")
 * ```
 */
class ToastHostState {

    private var nextId = 0L
    internal var currentToast by mutableStateOf<ToastMessage?>(null)
        private set

    /**
     * Show a toast message with the given [text] and [duration].
     *
     * If a toast is already visible, it will be replaced by this one
     * (the new toast appears immediately with a fresh animation).
     */
    fun show(
        text: String,
        duration: ToastDuration = ToastDuration.SHORT,
    ) {
        currentToast = ToastMessage(
            id = nextId++,
            text = text,
            duration = duration,
        )
    }

    /**
     * Dismiss the current toast immediately.
     */
    internal fun dismiss() {
        currentToast = null
    }
}

/**
 * CompositionLocal providing the [ToastHostState] for showing toasts.
 *
 * Provide this at the app root via [CompositionLocalProvider]:
 * ```kotlin
 * val toastHostState = remember { ToastHostState() }
 * CompositionLocalProvider(LocalToastHost provides toastHostState) {
 *     // Your app content
 *     ToastHost(state = toastHostState)
 * }
 * ```
 */
val LocalToastHost = staticCompositionLocalOf { ToastHostState() }

/**
 * Renders the animated toast overlay at the bottom center of the screen.
 *
 * Place this composable as an overlay on top of your app content, typically
 * as the last child of a [Box] that fills the screen.
 *
 * @param state The [ToastHostState] to observe for new toasts.
 */
@Composable
fun ToastHost(
    state: ToastHostState = LocalToastHost.current,
) {
    val toast = state.currentToast

    // Auto-dismiss after the configured duration
    val displayMillis = when (toast?.duration) {
        ToastDuration.SHORT -> 2000L
        ToastDuration.LONG -> 4000L
        null -> 0L
    }

    LaunchedEffect(toast?.id) {
        if (toast != null && displayMillis > 0L) {
            delay(displayMillis)
            state.dismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = toast,
            transitionSpec = {
                val enter: EnterTransition = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn()
                val exit: ExitTransition = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                enter togetherWith exit
            },
            label = "toast",
        ) { visibleToast ->
            if (visibleToast != null) {
                MacOSToastContent(text = visibleToast.text)
            }
        }
    }
}

/**
 * The visual toast card composable.
 *
 * Renders a Material 3 surface with rounded corners, appropriate padding,
 * and the standard "onSurface" color scheme.
 */
@Composable
private fun MacOSToastContent(
    text: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.85f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
