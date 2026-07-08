package app.anikku.macos.player

import com.sun.jna.Memory
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt

private val logger = KotlinLogging.logger {}

/**
 * Software renderer for mpv video frames using the libmpv render API.
 *
 * Creates a [mpv_render_context] with `MPV_RENDER_API_TYPE_SW` and renders
 * video frames into a CPU-side RGBA buffer. The frames are then converted
 * to [BufferedImage] objects suitable for display in Compose Desktop via
 * [toComposeImageBitmap][androidx.compose.ui.graphics.toComposeImageBitmap].
 *
 * ## Usage
 *
 * ```kotlin
 * val renderer = MPVSoftwareRenderer(mpvHandle)
 * renderer.create()
 *
 * // When a new frame is needed:
 * val image: BufferedImage? = renderer.render()
 * ```
 *
 * ## Lifecycle
 *
 * 1. Call [create] after mpv_initialize to create the render context.
 * 2. Call [updateVideoSize] when [MPVLib.MPV_EVENT_VIDEO_RECONFIG] fires
 *    to allocate a properly-sized buffer.
 * 3. Call [render] periodically (e.g., every 40ms) to get the latest frame.
 * 4. Call [dispose] on player shutdown.
 *
 * @param mpvHandle The initialized mpv handle (must have `vo=libmpv` set).
 */
class MPVSoftwareRenderer(
    private val mpvHandle: Pointer,
) {

    private var renderContext: Pointer? = null

    /** Current video dimensions (0 = unknown). Set via [updateVideoSize]. */
    @Volatile
    var videoWidth: Int = 0
        private set

    @Volatile
    var videoHeight: Int = 0
        private set

    /** Native memory buffer for raw RGBA pixel data. */
    private var pixelBuffer: Memory? = null

    /** Reusable [BufferedImage] for frame output. */
    private var frameImage: BufferedImage? = null

    /** Reusable byte array for copying native → Java heap (reduces GC). */
    private var rawByteBuffer: ByteArray? = null

    /** Whether the render context has been successfully created. */
    var isReady: Boolean = false
        private set

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Create the software render context.
     * Must be called after [MPVLib.initialize] and before loading any file.
     *
     * @return true on success, false if the context could not be created.
     */
    fun create(): Boolean {
        if (isReady) return true

        try {
            val ctx = MPVLib.renderContextCreate(mpvHandle)
            if (ctx != null) {
                renderContext = ctx
                isReady = true
                logger.info { "MPV software render context created" }
                return true
            } else {
                logger.error { "Failed to create MPV render context" }
                return false
            }
        } catch (e: Exception) {
            logger.error(e) { "Exception creating MPV render context" }
            return false
        }
    }

    /**
     * Update the video dimensions. Must be called when
     * [MPVLib.MPV_EVENT_VIDEO_RECONFIG] is received or when the surface is
     * resized.
     *
     * Reallocates the pixel buffer if the size has changed.
     */
    fun updateVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == videoWidth && height == videoHeight) return

        videoWidth = width
        videoHeight = height

        // Allocate native pixel buffer (4 bytes per pixel, "rgb0" format)
        val newSize = width * height * 4
        pixelBuffer = Memory(newSize.toLong())
        frameImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)

        logger.info { "Video surface updated: ${width}x${height}" }
    }

    /**
     * Render the current video frame.
     *
     * Call this periodically (e.g., every 40ms for 25fps) to get the latest
     * decoded frame. If a frame is not yet available, returns null.
     *
     * @return A [BufferedImage] with the current frame, or null if no frame.
     */
    fun render(): BufferedImage? {
        val ctx = renderContext ?: return null
        val buffer = pixelBuffer ?: return null
        val image = frameImage ?: return null
        val width = videoWidth
        val height = videoHeight
        if (width <= 0 || height <= 0) return null

        try {
            val stride = width * 4
            val sizeParams = Memory(8).also { mem ->
                mem.setInt(0, width)
                mem.setInt(4, height)
            }
            val strideParam = Memory(8).also { mem ->
                mem.setLong(0, stride.toLong())
            }

            // Build render params array
            val params = MPVLib.buildRenderParams(
                MPVLib.RENDER_PARAM_SW_SIZE to sizeParams,
                MPVLib.RENDER_PARAM_SW_FORMAT to Memory(5L).also { it.setString(0, MPVLib.RENDER_FORMAT_RGB0) },
                MPVLib.RENDER_PARAM_SW_STRIDE to strideParam,
                MPVLib.RENDER_PARAM_SW_POINTER to buffer,
            )

            val result = MPVLib.renderContextRender(ctx, params)
            if (result < 0) {
                // No frame available yet — this is normal during buffering
                return null
            }

            // Copy from native buffer to BufferedImage
            // MPV gives us "rgb0" format: R, G, B, 0 per pixel
            // BufferedImage.TYPE_INT_ARGB_PRE needs A, R, G, B packed into int
            copyBufferToImage(buffer, image, width, height, stride)

            return image
        } catch (e: Exception) {
            logger.debug { "Render frame failed (normal during buffering): ${e.message}" }
            return null
        }
    }

    /**
     * Copy raw RGBA pixel data from a native [Memory] buffer into a
     * [BufferedImage], converting from "rgb0" to ARGB format (alpha = 255).
     */
    private fun copyBufferToImage(
        nativeBuffer: Memory,
        image: BufferedImage,
        width: Int,
        height: Int,
        stride: Int,
    ) {
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        val byteCount = height * stride

        // Reuse the byte array to reduce GC pressure
        val rawBytes = rawByteBuffer?.takeIf { it.size >= byteCount }
            ?: ByteArray(byteCount).also { rawByteBuffer = it }
        nativeBuffer.read(0, rawBytes, 0, byteCount)

        // Convert from "rgb0" (R,G,B,0) to ARGB int (0xFF,R,G,B)
        for (y in 0 until height) {
            val rowOffset = y * stride
            for (x in 0 until width) {
                val srcIdx = rowOffset + x * 4
                val destIdx = y * width + x
                val r = rawBytes[srcIdx].toInt() and 0xFF
                val g = rawBytes[srcIdx + 1].toInt() and 0xFF
                val b = rawBytes[srcIdx + 2].toInt() and 0xFF
                pixels[destIdx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    /**
     * Dispose of the render context and free resources.
     */
    fun dispose() {
        renderContext?.let { MPVLib.renderContextFree(it) }
        renderContext = null
        pixelBuffer = null
        frameImage = null
        isReady = false
        videoWidth = 0
        videoHeight = 0
        logger.info { "MPV software renderer disposed" }
    }
}
