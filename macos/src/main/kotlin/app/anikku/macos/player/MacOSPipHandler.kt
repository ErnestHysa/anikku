package app.anikku.macos.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState

/**
 * macOS-native Picture-in-Picture handler.
 *
 * Creates a secondary always-on-top undecorated window for video playback,
 * mimicking the Android PiP experience. This approach is simpler than
 * integrating with AVKit's native PiP API.
 *
 * ## Usage
 *
 * ```kotlin
 * val pipHandler = remember { MacOSPipHandler() }
 *
 * // Open PiP window
 * pipHandler.openPipWindow(playerViewModel)
 *
 * // Close PiP window
 * pipHandler.closePipWindow()
 * ```
 *
 * The PiP window is:
 * - Always on top
 * - Undecorated (no title bar)
 * - Resizable (small, ~320x240 default)
 * - Independent of the main window
 * - Closes automatically when the main app exits
 */
class MacOSPipHandler {

    /** Whether the PiP window is currently visible. */
    var isPipVisible: Boolean = false
        private set

    /** The current PiP window title. */
    var pipTitle: String = ""
        private set

    /** The mpv handle for the video surface in the PiP window. */
    private var mpvHandle: com.sun.jna.Pointer? = null

    /**
     * Open the PiP window.
     *
     * @param title The title/description for the PiP window.
     * @param handle The mpv handle for video rendering.
     * @return true if the PiP window was opened.
     */
    fun openPipWindow(title: String, handle: com.sun.jna.Pointer?): Boolean {
        pipTitle = title
        mpvHandle = handle
        isPipVisible = true
        return true
    }

    /**
     * Close the PiP window.
     */
    fun closePipWindow() {
        isPipVisible = false
        mpvHandle = null
    }

    /**
     * Toggle PiP window visibility.
     */
    fun togglePip(title: String, handle: com.sun.jna.Pointer?): Boolean {
        if (isPipVisible) {
            closePipWindow()
            return false
        } else {
            return openPipWindow(title, handle)
        }
    }
}

/**
 * Composable that renders the PiP window when [pipHandler] requests one.
 *
 * Must be placed at the top level of the composable tree (alongside the main Window).
 * The PiP window is a secondary always-on-top window.
 *
 * @param pipHandler The PiP handler controlling visibility.
 * @param mpvHandle The mpv handle for video surface rendering.
 * @param onClose Called when the user closes the PiP window.
 */
@Composable
fun PipWindow(
    pipHandler: MacOSPipHandler,
    mpvHandle: com.sun.jna.Pointer?,
    onClose: () -> Unit,
) {
    if (pipHandler.isPipVisible) {
        val pipWindowState = rememberWindowState(
            placement = WindowPlacement.Floating,
            size = DpSize(320.dp, 240.dp),
        )

        Window(
            onCloseRequest = {
                pipHandler.closePipWindow()
                onClose()
            },
            title = pipHandler.pipTitle,
            state = pipWindowState,
            alwaysOnTop = true,
        ) {
            // Render the video surface inside the PiP window
            // Only if mpv is available
            if (MPVLib.isAvailable && mpvHandle != null) {
                MPVVideoSurface(
                    mpvHandle = mpvHandle,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Dispose effect to clean up when PiP window closes
            DisposableEffect(Unit) {
                onDispose {
                    // Clean up any PiP-specific resources
                }
            }
        }
    }
}
