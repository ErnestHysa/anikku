package app.anikku.macos.platform.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for [MacOSHttpServer] — the embedded HTTP server
 * that serves local video files to mpv (Phase 6.6).
 *
 * MIME type tests call [MacOSHttpServer.getMimeType] directly
 * (internal visibility for testability).
 * Server lifecycle tests verify start/stop behavior.
 */
class MacOSHttpServerTest {

    private val testDir = File(System.getProperty("java.io.tmpdir"), "anikku-http-test")
    private val server = MacOSHttpServer(downloadsDir = testDir)

    // =========================================================================
    // Initialization
    // =========================================================================

    @Test
    fun `server initializes with isRunning false`() {
        assertFalse(server.isRunning)
    }

    @Test
    fun `server initializes with auto-assigned port when port is 0`() {
        val s = MacOSHttpServer(downloadsDir = testDir, port = 0)
        assertFalse(s.isRunning)
    }

    @Test
    fun `server initializes with explicit port`() {
        val s = MacOSHttpServer(downloadsDir = testDir, port = 12345)
        assertFalse(s.isRunning)
    }

    // =========================================================================
    // URL construction
    // =========================================================================

    @Test
    fun `getStreamUrl returns null when server is not running`() {
        val file = File(testDir, "test.mp4")
        val url = server.getStreamUrl(file)
        assertNull(url)
    }

    @Test
    fun `getStreamUrl returns null for non-existent file`() {
        val nonExistent = File(testDir, "nonexistent.mp4")
        assertFalse(nonExistent.exists())
        assertNull(server.getStreamUrl(nonExistent))
    }

    @Test
    fun `getStreamUrl with downloadId returns null when not running`() {
        assertNull(server.getStreamUrl(downloadId = 42L))
    }

    // =========================================================================
    // MIME type mapping — calls the actual server method
    // =========================================================================

    @Test
    fun `MIME type for mp4 is video-slash-mp4`() {
        assertEquals("video/mp4", server.getMimeType("mp4"))
    }

    @Test
    fun `MIME type for mkv is video-slash-x-matroska`() {
        assertEquals("video/x-matroska", server.getMimeType("mkv"))
    }

    @Test
    fun `MIME type for webm is video-slash-webm`() {
        assertEquals("video/webm", server.getMimeType("webm"))
    }

    @Test
    fun `MIME type for avi is video-slash-x-msvideo`() {
        assertEquals("video/x-msvideo", server.getMimeType("avi"))
    }

    @Test
    fun `MIME type for mov is video-slash-quicktime`() {
        assertEquals("video/quicktime", server.getMimeType("mov"))
    }

    @Test
    fun `MIME type for m4v is video-slash-x-m4v`() {
        assertEquals("video/x-m4v", server.getMimeType("m4v"))
    }

    @Test
    fun `MIME type for mpg is video-slash-mpeg`() {
        assertEquals("video/mpeg", server.getMimeType("mpg"))
    }

    @Test
    fun `MIME type for mpeg is video-slash-mpeg`() {
        assertEquals("video/mpeg", server.getMimeType("mpeg"))
    }

    @Test
    fun `MIME type for flv is video-slash-x-flv`() {
        assertEquals("video/x-flv", server.getMimeType("flv"))
    }

    @Test
    fun `MIME type for wmv is video-slash-x-ms-wmv`() {
        assertEquals("video/x-ms-wmv", server.getMimeType("wmv"))
    }

    @Test
    fun `MIME type for 3gp is video-slash-3gpp`() {
        assertEquals("video/3gpp", server.getMimeType("3gp"))
    }

    @Test
    fun `MIME type for ts is video-slash-mp2t`() {
        assertEquals("video/mp2t", server.getMimeType("ts"))
    }

    @Test
    fun `MIME type for ogv is video-slash-ogg`() {
        assertEquals("video/ogg", server.getMimeType("ogv"))
    }

    @Test
    fun `MIME type for unknown extension is application-slash-octet-stream`() {
        assertEquals("application/octet-stream", server.getMimeType("xyz"))
        assertEquals("application/octet-stream", server.getMimeType(""))
    }

    @Test
    fun `MIME type handles uppercase extension`() {
        assertEquals("video/mp4", server.getMimeType("MP4"))
        assertEquals("video/x-matroska", server.getMimeType("MKV"))
        assertEquals("video/webm", server.getMimeType("WEBM"))
    }

    // =========================================================================
    // Server lifecycle
    // =========================================================================

    @Test
    fun `start and stop lifecycle`() {
        val s = MacOSHttpServer(downloadsDir = testDir)
        assertFalse(s.isRunning)

        s.startServer()
        assertTrue(s.isRunning)
        assertTrue(s.actualPort > 0, "Actual port should be > 0 after start")

        s.stopServer()
        assertFalse(s.isRunning)
    }

    @Test
    fun `double start is no-op`() {
        val s = MacOSHttpServer(downloadsDir = testDir)
        s.startServer()
        s.startServer() // Should not throw
        assertTrue(s.isRunning)
        s.stopServer()
    }

    @Test
    fun `double stop is no-op`() {
        val s = MacOSHttpServer(downloadsDir = testDir)
        s.stopServer() // Should not throw
        s.stopServer() // Should not throw
        assertFalse(s.isRunning)
    }
}
