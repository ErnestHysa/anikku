package app.anikku.macos.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/**
 * Target render interval in milliseconds (~33 fps).
 *
 * This is a target polling interval; the actual frame is only updated when
 * [MPVSoftwareRenderer.render] returns a new [BufferedImage].
 */
private const val FRAME_INTERVAL_MS = 30L

/**
 * Compose Desktop video surface for mpv software-rendered frames.
 *
 * Uses [MPVSoftwareRenderer] to pull decoded frames from libmpv and
 * renders them onto a Compose [Canvas] as [ImageBitmap]s.
 *
 * Replaces the earlier SwingPanel + AWT Canvas approach with a pure
 * Compose solution that works reliably on macOS without platform-specific
 * native window embedding.
 *
 * ## Rendering Flow
 *
 * ```
 * LaunchedEffect(renderer) ─► [MPVSoftwareRenderer.render()]
 *                                      │
 *                                      ▼
 *                              BufferedImage (ARGB)
 *                                      │
 *                                      ▼
 *                            .toComposeImageBitmap()
 *                                      │
 *                                      ▼
 *                              Canvas.drawImage()
 * ```
 *
 * @param mpvHandle The mpv core handle (null if mpv is unavailable).
 * @param renderer The [MPVSoftwareRenderer] instance, or null if not ready.
 * @param modifier Standard Compose modifier.
 */
@Composable
fun MPVVideoSurface(
    mpvHandle: Pointer?,
    renderer: MPVSoftwareRenderer?,
    modifier: Modifier = Modifier,
) {
    val mpvAvailable = mpvHandle != null && MPVLib.isAvailable

    // Current rendered frame as a Compose ImageBitmap
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }

    // Surface size for aspect-ratio-aware rendering
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }

    // Periodic render loop: pull frames from the software renderer.
    // Rendering is performed on Dispatchers.IO to avoid blocking the Compose
    // UI thread; only the resulting ImageBitmap is posted back to the UI.
    LaunchedEffect(renderer, mpvAvailable) {
        if (renderer == null || !mpvAvailable) return@LaunchedEffect

        while (isActive) {
            val startTime = System.nanoTime()

            val bufferedImage = withContext(Dispatchers.IO) {
                renderer.render()
            }
            if (bufferedImage != null) {
                currentFrame = bufferedImage.toComposeImageBitmap()
            }

            // Adaptive delay: aim for the target frame interval, but never
            // sleep a negative amount (i.e., if rendering took longer than the
            // target interval, immediately render the next frame).
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000L
            val sleepMs = (FRAME_INTERVAL_MS - elapsedMs).coerceAtLeast(0L)
            delay(sleepMs)
        }
    }

    // Clean up the bitmap reference on dispose
    DisposableEffect(renderer) {
        onDispose {
            currentFrame = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size -> surfaceSize = size }
            .onKeyEvent { event ->
                when (event.key) {
                    Key.Spacebar -> {
                        mpvHandle?.let { togglePause(it) }
                        true
                    }
                    Key.DirectionLeft -> {
                        mpvHandle?.let { seekRelative(it, -5.0) }
                        true
                    }
                    Key.DirectionRight -> {
                        mpvHandle?.let { seekRelative(it, 5.0) }
                        true
                    }
                    Key.DirectionUp -> {
                        mpvHandle?.let { adjustVolume(it, 5) }
                        true
                    }
                    Key.DirectionDown -> {
                        mpvHandle?.let { adjustVolume(it, -5) }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = currentFrame
        if (bitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawVideoFrame(bitmap)
            }
        }
    }
}

/**
 * Draw the video frame centered and scaled to fit the canvas,
 * maintaining the original aspect ratio.
 */
private fun DrawScope.drawVideoFrame(bitmap: ImageBitmap) {
    val frameWidth = bitmap.width.toFloat()
    val frameHeight = bitmap.height.toFloat()
    val canvasWidth = size.width
    val canvasHeight = size.height

    if (frameWidth <= 0f || frameHeight <= 0f || canvasWidth <= 0f || canvasHeight <= 0f) return

    val scaleX = canvasWidth / frameWidth
    val scaleY = canvasHeight / frameHeight
    val scale = minOf(scaleX, scaleY)

    val drawWidth = (frameWidth * scale).toInt()
    val drawHeight = (frameHeight * scale).toInt()

    val offsetX = ((canvasWidth - drawWidth) / 2f).toInt()
    val offsetY = ((canvasHeight - drawHeight) / 2f).toInt()

    drawImage(
        image = bitmap,
        dstOffset = IntOffset(offsetX, offsetY),
        dstSize = IntSize(drawWidth, drawHeight),
    )
}

// ---------------------------------------------------------------------------
// MPV utility functions (used by MPVVideoSurface and others)
// ---------------------------------------------------------------------------

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
