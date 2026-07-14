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
    var videoWidth: Int = 0
        private set

    var videoHeight: Int = 0
        private set

    /** Native memory buffer for raw RGBA pixel data. */
    private var pixelBuffer: Memory? = null

    /** Reusable [BufferedImage] for frame output. */
    private var frameImage: BufferedImage? = null

    /** Reusable [IntArray] for bulk native → Java heap copy (reduces GC). */
    private var rawIntBuffer: IntArray? = null

    /** Reusable native memory for render params (avoids per-frame allocation). */
    private var sizeParams: Memory? = null
    private var strideParam: Memory? = null
    private var formatParam: Memory? = null
    private var renderParams: Memory? = null

    /** Fallback byte buffer for non-bgr0 pixel formats. */
    private var rawByteBuffer: ByteArray? = null

    /** Whether the render context has been successfully created. */
    var isReady: Boolean = false
        private set

    /** Lock protecting mutable renderer state across threads. */
    private val lock = Any()

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
        synchronized(lock) {
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

        synchronized(lock) {
            videoWidth = width
            videoHeight = height

            // Allocate native pixel buffer (4 bytes per pixel, "bgr0" format)
            val newSize = width * height * 4
            pixelBuffer = Memory(newSize.toLong())
            // TYPE_INT_RGB stores pixels as 0x00RRGGBB. On little-endian (macOS)
            // this is laid out in memory as B, G, R, 0 — exactly matching mpv's
            // "bgr0" format, so we can copy the frame with a single bulk read.
            frameImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

            // Reuse render param memory; reallocate only when dimensions change.
            sizeParams = Memory(8).also { mem ->
                mem.setInt(0, width)
                mem.setInt(4, height)
            }
            strideParam = Memory(8).also { mem ->
                mem.setLong(0, width * 4L)
            }
            formatParam = Memory(5L).also { it.setString(0, MPVLib.RENDER_FORMAT_BGR0) }
            renderParams = buildRenderParamsForFormat(MPVLib.RENDER_FORMAT_BGR0)
        }

        logger.info { "Video surface updated: ${width}x${height}" }
    }

    /** Build the reusable render params memory for the given pixel format. */
    private fun buildRenderParamsForFormat(format: String): Memory {
        formatParam = Memory(5L).also { it.setString(0, format) }
        return MPVLib.buildRenderParams(
            MPVLib.RENDER_PARAM_SW_SIZE to sizeParams,
            MPVLib.RENDER_PARAM_SW_FORMAT to formatParam,
            MPVLib.RENDER_PARAM_SW_STRIDE to strideParam,
            MPVLib.RENDER_PARAM_SW_POINTER to pixelBuffer,
        )
    }

    /** Snapshot of renderer state captured under [lock] for a render call. */
    private data class RenderSnapshot(
        val buffer: Memory,
        val image: BufferedImage,
        val width: Int,
        val height: Int,
        val stride: Int,
        val params: Memory,
        val intBuffer: IntArray,
        /** Whether the snapshot is using the rgb0 fallback path. */
        val isRgb0Fallback: Boolean = false,
    )

    /**
     * Render the current video frame.
     *
     * Call this periodically (e.g., every 40ms for 25fps) to get the latest
     * decoded frame. If a frame is not yet available, returns null.
     *
     * @return A [BufferedImage] with the current frame, or null if no frame.
     */
    fun render(): BufferedImage? {
        // Snapshot mutable state under the lock so render() and
        // updateVideoSize()/dispose() cannot race.
        val snapshot = synchronized(lock) {
            val ctx = renderContext ?: return null
            val buffer = pixelBuffer ?: return null
            val image = frameImage ?: return null
            val width = videoWidth
            val height = videoHeight
            val params = renderParams ?: return null
            if (width <= 0 || height <= 0) return null
            val stride = width * 4
            val intBuffer = rawIntBuffer ?: IntArray(height * stride / 4).also { rawIntBuffer = it }
            val isRgb0 = formatParam?.getString(0) == MPVLib.RENDER_FORMAT_RGB0
            RenderSnapshot(buffer, image, width, height, stride, params, intBuffer, isRgb0)
        }

        try {
            val result = MPVLib.renderContextRender(snapshot.ctx, snapshot.params)
            if (result < 0) {
                // If we got a format error and haven't tried the fallback yet,
                // switch to rgb0 and retry on the next frame.
                if (result == MPVLib.ERROR_INVALID_PARAMETER && !snapshot.isRgb0Fallback) {
                    if (switchToRgb0Fallback()) {
                        return null // retry next frame with fallback
                    }
                }
                // No frame available yet — this is normal during buffering
                return null
            }

            // Copy from native buffer to BufferedImage.
            if (snapshot.isRgb0Fallback) {
                copyBufferToImageRgb0(snapshot)
            } else {
                // Because we requested "bgr0" and use TYPE_INT_RGB, the
                // in-memory layout matches exactly, so a single bulk int copy
                // is sufficient.
                copyBufferToImage(snapshot)
            }

            return snapshot.image
        } catch (e: Exception) {
            logger.debug { "Render frame failed (normal during buffering): ${e.message}" }
            return null
        }
    }

    /** Convenience access to the render context for the snapshot. */
    private val RenderSnapshot.ctx get() = renderContext

    /**
     * Attempt to switch to the fallback rgb0 format.
     * @return true if the switch happened, false if already using fallback.
     */
    private fun switchToRgb0Fallback(): Boolean {
        synchronized(lock) {
            if (renderParams == null || formatParam?.getString(0) == MPVLib.RENDER_FORMAT_RGB0) {
                return false
            }
            logger.warn { "bgr0 not supported by this libmpv build — falling back to rgb0 + conversion" }
            renderParams = buildRenderParamsForFormat(MPVLib.RENDER_FORMAT_RGB0)
            return true
        }
    }

    /**
     * Copy raw BGR0/RGB0 pixel data from a native [Memory] buffer into a
     * [BufferedImage].
     *
     * Because mpv writes "bgr0" and [BufferedImage.TYPE_INT_RGB] stores
     * 0x00RRGGBB (little-endian bytes B, G, R, 0), the layouts match exactly,
     * so we can copy the entire frame in one bulk operation.
     */
    private fun copyBufferToImage(snapshot: RenderSnapshot) {
        val (nativeBuffer, image, width, height, stride, _, rawInts) = snapshot
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        val intCount = height * stride / 4

        // Single bulk native → JVM copy. On little-endian macOS the bytes
        // B, G, R, 0 produced by mpv become the int 0x00RRGGBB, which is
        // exactly what TYPE_INT_RGB expects.
        nativeBuffer.read(0, rawInts, 0, intCount)

        // BufferedImage may have a larger int[] than width*height due to
        // scanline padding, so copy row-by-row to handle stride differences.
        if (stride == width * 4) {
            // Fast path: no padding, copy entire frame at once
            System.arraycopy(rawInts, 0, pixels, 0, width * height)
        } else {
            // Slow path: copy each row separately to account for stride
            for (y in 0 until height) {
                val srcPos = y * stride / 4
                val dstPos = y * width
                System.arraycopy(rawInts, srcPos, pixels, dstPos, width)
            }
        }
    }

    /**
     * Fallback copy for "rgb0" format (R, G, B, 0 in memory).
     * Needed when the installed libmpv does not support "bgr0".
     */
    private fun copyBufferToImageRgb0(snapshot: RenderSnapshot) {
        val (nativeBuffer, image, width, height, stride) = snapshot
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        val byteCount = height * stride

        // Use a local ByteArray to avoid any thread-safety concerns with the
        // fallback path. This path is only used when bgr0 is unsupported.
        val rawBytes = ByteArray(byteCount)
        nativeBuffer.read(0, rawBytes, 0, byteCount)

        for (y in 0 until height) {
            val rowOffset = y * stride
            for (x in 0 until width) {
                val srcIdx = rowOffset + x * 4
                val destIdx = y * width + x
                val r = rawBytes[srcIdx].toInt() and 0xFF
                val g = rawBytes[srcIdx + 1].toInt() and 0xFF
                val b = rawBytes[srcIdx + 2].toInt() and 0xFF
                pixels[destIdx] = (r shl 16) or (g shl 8) or b
            }
        }
    }

    /**
     * Dispose of the render context and free resources.
     */
    fun dispose() {
        synchronized(lock) {
            renderContext?.let { MPVLib.renderContextFree(it) }
            renderContext = null
            pixelBuffer = null
            frameImage = null
            rawIntBuffer = null
            rawByteBuffer = null
            sizeParams = null
            strideParam = null
            formatParam = null
            renderParams = null
            isReady = false
            videoWidth = 0
            videoHeight = 0
        }
        logger.info { "MPV software renderer disposed" }
    }
}
