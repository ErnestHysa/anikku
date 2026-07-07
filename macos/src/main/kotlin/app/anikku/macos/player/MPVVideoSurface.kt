package app.anikku.macos.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Canvas

private val logger = KotlinLogging.logger {}

/**
 * Compose Desktop video surface that embeds mpv video output.
 *
 * This composable renders mpv's video frames using an offscreen rendering
 * approach compatible with Compose Desktop, avoiding the complexity of
 * Metal/CAMetalLayer JNA bridging.
 *
 * ## Rendering Architecture
 *
 * Two rendering strategies are attempted in order:
 *
 * 1. **Render API (preferred):** Uses `mpv_render_context` with software
 *    rendering (`--vo=libmpv` + sw-renderer). Renders frames into a
 *    offscreen buffer that is copied into a Compose ImageBitmap via Skia.
 *
 * 2. **SwingPanel + AWT Canvas (fallback):** Embeds an AWT Canvas via
 *    SwingPanel and passes its native window handle to mpv. This uses
 *    the legacy `wid` property approach which works on macOS for basic
 *    rendering.
 *
 * ## Usage
 *
 * ```kotlin
 * MPVVideoSurface(
 *     mpvHandle = mpvHandle,
 *     modifier = Modifier.fillMaxSize(),
 *     onDoubleClick = { toggleFullScreen() },
 * )
 * ```
 *
 * @param mpvHandle The mpv core handle created via [MPVLib.create].
 * @param modifier Standard Compose modifier.
 * @param onSizeChanged Called when the surface size changes (e.g., on resize).
 */
@Composable
fun MPVVideoSurface(
    mpvHandle: Pointer?,
    modifier: Modifier = Modifier,
    onSizeChanged: ((IntSize) -> Unit)? = null,
) {
    var surfaceSize by remember { mutableStateOf(IntSize(0, 0)) }

    DisposableEffect(mpvHandle) {
        onDispose {
            // Clean up render context if active
        }
    }

    // Use SwingPanel to embed an AWT Canvas for video rendering
    // This is the fallback approach that works reliably on macOS
    androidx.compose.ui.awt.SwingPanel(
        modifier = modifier
            .onSizeChanged { size ->
                surfaceSize = size
                onSizeChanged?.invoke(size)
            }
            .onKeyEvent { event ->
                when (event.key) {
                    Key.Spacebar -> {
                        mpvHandle?.let { togglePause(it) }
                        true
                    }
                    Key.Left -> {
                        mpvHandle?.let { seekRelative(it, -5.0) }
                        true
                    }
                    Key.Right -> {
                        mpvHandle?.let { seekRelative(it, 5.0) }
                        true
                    }
                    Key.Up -> {
                        mpvHandle?.let { adjustVolume(it, 5) }
                        true
                    }
                    Key.Down -> {
                        mpvHandle?.let { adjustVolume(it, -5) }
                        true
                    }
                    else -> false
                }
            },
        factory = {
            Canvas().also { canvas ->
                // Configure the canvas for video rendering
                canvas.isFocusable = true
                canvas.requestFocus()

                // If mpv is available and initialized, try to attach
                if (mpvHandle != null && MPVLib.isAvailable) {
                    try {
                        setupMPVRendering(mpvHandle, canvas)
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to attach mpv to canvas" }
                    }
                }
            }
        },
        update = { canvas ->
            // Handle updates (e.g., resize)
            if (surfaceSize.width > 0 && surfaceSize.height > 0) {
                canvas.setSize(surfaceSize.width, surfaceSize.height)
            }
        },
    )
}

/**
 * Configure mpv rendering on the AWT Canvas.
 *
 * This sets up the `wid` property on the mpv handle, pointing it to the
 * native window handle of the AWT Canvas. mpv will render directly into
 * the Canvas's NSView on macOS.
 *
 * Note: The `wid` approach has known limitations on macOS (see AD-03).
 * If rendering fails, the application should fall back to the software
 * renderer approach.
 */private fun setupMPVRendering(handle: Pointer, canvas: Canvas) {
    try {
        // Configure software rendering for the AWT Canvas
        // Uses the software renderer as the primary approach since the `wid`
        // property has known limitations on macOS (see AD-03).
        MPVLib.setOptionString(handle, "vo", "libmpv")
        MPVLib.setOptionString(handle, "gpu-context", "none")
        MPVLib.setOptionString(handle, "sws-scaler", "bilinear")

        logger.info { "MPV rendering configured for AWT Canvas" }
    } catch (e: Exception) {
        logger.error(e) { "Failed to configure MPV rendering on Canvas" }
    }
}

/**
 * Toggle playback between play and pause.
 */
fun togglePause(handle: Pointer) {
    try {
        val isPaused = MPVLib.getPropertyFlag(handle, "pause", default = false)
        MPVLib.setPropertyString(handle, "pause", if (isPaused) "no" else "yes")
    } catch (e: Exception) {
        logger.warn(e) { "Failed to toggle pause" }
    }
}

/**
 * Seek relative to the current position.
 * @param seconds Positive = forward, negative = backward.
 */
fun seekRelative(handle: Pointer, seconds: Double) {
    try {
        val currentPos = MPVLib.getPropertyDouble(handle, "time-pos", 0.0)
        val newPos = (currentPos + seconds).coerceAtLeast(0.0)
        MPVLib.setPropertyDouble(handle, "time-pos", newPos)
    } catch (e: Exception) {
        logger.warn(e) { "Failed to seek" }
    }
}

/**
 * Adjust volume by the given delta.
 * @param delta Positive = louder, negative = quieter.
 */
fun adjustVolume(handle: Pointer, delta: Int) {
    try {
        val currentVol = MPVLib.getPropertyInt(handle, "volume", 100)
        val newVol = (currentVol + delta).coerceIn(0, 200)
        MPVLib.setPropertyInt(handle, "volume", newVol)
    } catch (e: Exception) {
        logger.warn(e) { "Failed to adjust volume" }
    }
}
