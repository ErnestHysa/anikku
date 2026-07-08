package app.anikku.macos.platform.torrent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

class TorrentServerBridgeTest {

    @Test
    fun `initial status is stopped`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        assertEquals(ServerStatus.STOPPED, bridge.serverStatus.value)
    }

    @Test
    fun `isRunning returns false initially`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        assertFalse(bridge.isRunning)
    }

    @Test
    fun `isBinaryAvailable returns false when no binary`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/nonexistent"),
            dataDirectory = File("/tmp"),
        )
        assertFalse(bridge.isBinaryAvailable)
    }

    @Test
    fun `stop is safe when not running`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        bridge.stop() // Should not throw
        assert(true)
    }

    @Test
    fun `listTorrents returns empty when not running`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        val torrents = bridge.listTorrents()
        assertEquals(0, torrents.size)
    }

    @Test
    fun `addTorrent returns null when not running`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        val url = bridge.addTorrent("magnet:?xt=urn:btih:test")
        assertEquals(null, url)
    }

    @Test
    fun `removeTorrent returns false when not running`() {
        val bridge = TorrentServerBridge(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            binDirectory = File("/tmp"),
            dataDirectory = File("/tmp"),
        )
        assertFalse(bridge.removeTorrent("test_hash"))
    }
}
