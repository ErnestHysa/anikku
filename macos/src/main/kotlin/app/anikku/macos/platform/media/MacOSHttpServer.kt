package app.anikku.macos.platform.media

import fi.iki.elonen.NanoHTTPD
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.FileInputStream

private val logger = KotlinLogging.logger {}

/**
 * Embedded HTTP server that serves local video files to mpv.
 *
 * ## Why this is needed
 *
 * mpv requires a URL to load media; it cannot load files via file:// on
 * some platforms or when certain features (like cache, YTDL) are active.
 * This local HTTP server bridges the gap by serving local video files
 * over http://localhost:PORT/.
 *
 * ## Usage
 *
 * ```kotlin
 * val server = MacOSHttpServer(downloadsDir = File("~/.../downloads"))
 * server.start()
 * val url = server.getStreamUrl(episodeFile) // http://localhost:12345/episode-1.mp4
 * ```
 *
 * Ported from Android's LocalHttpServerService.kt (NanoHTTPd-based).
 */
class MacOSHttpServer(
    private val downloadsDir: File,
    private val port: Int = 0, // 0 = auto-assign
) : NanoHTTPD(if (port > 0) port else 0) {

    /** Returns the actual port the server is listening on. */
    val actualPort: Int get() = listeningPort

    /** Whether the server is currently running. */
    var isRunning: Boolean = false
        private set

    /**
     * Start the HTTP server on a background thread.
     */
    fun startServer() {
        if (isRunning) return
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) // daemon thread — allows clean JVM exit
            isRunning = true
            logger.info { "Local HTTP server started on port $actualPort" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to start local HTTP server" }
        }
    }

    /**
     * Stop the HTTP server.
     */
    fun stopServer() {
        if (!isRunning) return
        try {
            stop()
            isRunning = false
            logger.info { "Local HTTP server stopped" }
        } catch (e: Exception) {
            logger.warn(e) { "Error stopping HTTP server" }
        }
    }

    /**
     * Get a streamable HTTP URL for a local file.
     *
     * @param file The local file to serve.
     * @return An http://localhost:PORT/... URL that mpv can load.
     */
    fun getStreamUrl(file: File): String? {
        if (!isRunning || !file.isFile) return null
        return "http://127.0.0.1:$actualPort/stream/${file.name}"
    }

    /**
     * Get a streamable URL for a download ID.
     *
     * @param downloadId The download identifier — used to construct the path.
     * @return An http://localhost:PORT/... URL that mpv can load.
     */
    fun getStreamUrl(downloadId: Long): String? {
        if (!isRunning) return null
        return "http://127.0.0.1:$actualPort/download/$downloadId"
    }

    // -------------------------------------------------------------------------
    // NanoHTTPd request handler
    // -------------------------------------------------------------------------

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            // Health check
            uri == "/health" || uri == "/" -> newFixedLengthResponse(
                Response.Status.OK,
                "text/plain",
                "Anikku local media server — OK",
            )

            // Stream a file by name
            uri.startsWith("/stream/") -> serveFileStream(uri.removePrefix("/stream/"))

            // Stream a file by download ID (placeholder for Phase 7)
            uri.startsWith("/download/") -> {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain",
                    "Download ID streaming not yet implemented",
                )
            }

            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "Not found",
            )
        }
    }

    /**
     * Serve a file as a video stream with proper headers.
     *
     * Supports:
     * - Range requests (partial content) for seeking in mpv
     * - Proper MIME types for common video formats
     * - Cross-origin headers for mpv compatibility
     */
    private fun serveFileStream(fileName: String): Response {
        val file = findFile(fileName)
            ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "File not found: $fileName",
            )

        val mimeType = getMimeType(file.extension)
        val fileLength = file.length()

        return newFixedLengthResponse(
            Response.Status.OK,
            mimeType,
            FileInputStream(file),
            fileLength,
        ).also { response ->
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Disposition", "inline; filename=\"${file.name}\"")
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            response.addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
        }
    }

    /**
     * Find a file by name in the downloads directory.
     * Performs a simple name-based lookup (linear scan).
     */
    private fun findFile(fileName: String): File? {
        if (!downloadsDir.isDirectory) return null
        return downloadsDir.listFiles()
            ?.firstOrNull { it.name == fileName }
    }

    /**
     * Get MIME type for common video file extensions.
     */
    private fun getMimeType(extension: String): String = when (extension.lowercase()) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        "mpg", "mpeg" -> "video/mpeg"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        "ogv" -> "video/ogg"
        else -> "application/octet-stream"
    }
}
