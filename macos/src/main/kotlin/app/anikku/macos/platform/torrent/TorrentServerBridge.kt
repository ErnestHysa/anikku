package app.anikku.macos.platform.torrent

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * TorrServer bridge for macOS.
 *
 * TorrServer is a lightweight torrent streaming server that converts
 * torrent files into HTTP streams. The Android app bundles the TorrServer
 * binary and manages its lifecycle. This class does the same for macOS.
 *
 * ## TorrServer Binary
 *
 * On macOS, TorrServer must be downloaded or bundled:
 * - Download from: https://github.com/YouROK/TorrServer/releases
 * - Place in: `~Library/Application Support/Anikku/torrserver/TorrServer-macOS-arm64` (Apple Silicon)
 * - Or: `TorrServer-macOS-amd64` (Intel)
 *
 * ## Usage
 *
 * ```kotlin
 * val bridge = TorrentServerBridge(scope, storageProvider)
 * bridge.start()
 *
 * // Convert torrent to stream
 * val streamUrl = bridge.addTorrent(torrentUrl)
 * // Pass streamUrl to mpv for playback
 *
 * bridge.stop()
 * ```
 */
class TorrentServerBridge(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val binDirectory: File,
    private val dataDirectory: File,
    private val torrServerHost: String = "127.0.0.1",
    private val torrServerPort: Int = 8090,
) {

    private var serverProcess: Process? = null
    private var processWatcher: Job? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    /** Whether the TorrServer is running and healthy. */
    val isRunning: Boolean get() = _serverStatus.value == ServerStatus.RUNNING

    /** The base URL for TorrServer API calls. */
    private val apiBase: String get() = "http://$torrServerHost:$torrServerPort"

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Start the TorrServer process.
     *
     * @param timeoutSeconds Maximum time to wait for the server to become healthy.
     * @return true if the server started successfully.
     */
    suspend fun start(timeoutSeconds: Int = 30): Boolean {
        if (isRunning) return true

        _serverStatus.value = ServerStatus.STARTING

        val binary = findServerBinary() ?: run {
            logger.warn { "TorrServer binary not found" }
            _serverStatus.value = ServerStatus.ERROR
            return false
        }

        return try {
            val processBuilder = ProcessBuilder(
                binary.absolutePath,
                "--port", torrServerPort.toString(),
                "--path", dataDirectory.absolutePath,
            )
            processBuilder.directory(binDirectory)
            processBuilder.environment()["HOME"] = System.getProperty("user.home")

            serverProcess = processBuilder.start()

            // Wait for server to become healthy
            val startTime = System.currentTimeMillis()
            var healthy = false

            while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000L) {
                if (isServerHealthy()) {
                    healthy = true
                    break
                }
                delay(500)
            }

            if (healthy) {
                _serverStatus.value = ServerStatus.RUNNING
                startProcessWatcher()
                logger.info { "TorrServer started on $apiBase" }
                true
            } else {
                logger.warn { "TorrServer failed to become healthy within ${timeoutSeconds}s" }
                _serverStatus.value = ServerStatus.ERROR
                stop()
                false
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to start TorrServer" }
            _serverStatus.value = ServerStatus.ERROR
            false
        }
    }

    /**
     * Stop the TorrServer process.
     */
    fun stop() {
        processWatcher?.cancel()
        serverProcess?.destroyForcibly()
        serverProcess = null
        _serverStatus.value = ServerStatus.STOPPED
        logger.info { "TorrServer stopped" }
    }

    /**
     * Restart the TorrServer process.
     */
    suspend fun restart(timeoutSeconds: Int = 30): Boolean {
        stop()
        delay(1000)
        return start(timeoutSeconds)
    }

    // -------------------------------------------------------------------------
    // Torrent Operations
    // -------------------------------------------------------------------------

    /**
     * Add a torrent to TorrServer and get a streaming URL.
     *
     * @param torrentUrl URL to a .torrent file or magnet link.
     * @param title Optional title for the torrent.
     * @return The HTTP streaming URL to pass to mpv, or null on failure.
     */
    fun addTorrent(torrentUrl: String, title: String? = null): String? {
        if (!isRunning) {
            logger.warn { "TorrServer not running" }
            return null
        }

        return try {
            val requestBody = FormBody.Builder()
                .add("link", torrentUrl)
                .add("download", "true")
                .add("save", "true")
                .apply { if (title != null) add("title", title) }
                .build()

            val request = Request.Builder()
                .url("$apiBase/torrents")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                logger.warn { "Failed to add torrent: ${response.code}" }
                return null
            }

            // Extract the torrent hash/ID from the response
            // TorrServer returns JSON with the torrent info
            val torrentId = extractTorrentId(bodyString) ?: return null

            // Build streaming URL
            "$apiBase/stream/$torrentId:1"
        } catch (e: Exception) {
            logger.error(e) { "Failed to add torrent: $torrentUrl" }
            null
        }
    }

    /**
     * Remove a torrent from TorrServer.
     *
     * @param torrentId The torrent hash or ID to remove.
     */
    fun removeTorrent(torrentId: String): Boolean {
        if (!isRunning) return false

        return try {
            val request = Request.Builder()
                .url("$apiBase/torrents/$torrentId")
                .delete()
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            logger.warn(e) { "Failed to remove torrent: $torrentId" }
            false
        }
    }

    /**
     * List all torrents in TorrServer.
     *
     * @return List of torrent info objects.
     */
    fun listTorrents(): List<TorrentInfo> {
        if (!isRunning) return emptyList()

        return try {
            val request = Request.Builder()
                .url("$apiBase/torrents")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: return emptyList()

            parseTorrentList(bodyString)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to list torrents" }
            emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun findServerBinary(): File? {
        val candidates = listOf(
            File(binDirectory, "TorrServer-macOS-arm64"),
            File(binDirectory, "TorrServer-macOS-amd64"),
            File(binDirectory, "TorrServer"),
        )
        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }

    private fun isServerHealthy(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$apiBase/health")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    private fun startProcessWatcher() {
        processWatcher = scope.launch {
            while (isActive) {
                delay(10_000)
                if (serverProcess?.isAlive == false) {
                    logger.warn { "TorrServer process died unexpectedly" }
                    _serverStatus.value = ServerStatus.ERROR
                    break
                }
            }
        }
    }

    private fun extractTorrentId(responseBody: String): String? {
        // Parse TorrServer JSON response to extract torrent hash
        return try {
            val json = org.json.JSONObject(responseBody)
            json.optString("hash", null) ?: json.optString("id", null)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTorrentList(responseBody: String): List<TorrentInfo> {
        return try {
            val jsonArray = org.json.JSONArray(responseBody)
            (0 until jsonArray.length()).map { index ->
                val obj = jsonArray.getJSONObject(index)
                TorrentInfo(
                    hash = obj.optString("hash", ""),
                    title = obj.optString("title", "Unknown"),
                    size = obj.optLong("size", 0),
                    progress = (obj.optDouble("percent_downloaded", 0.0) / 100.0).toFloat(),
                    status = obj.optString("status", "unknown"),
                    seeders = obj.optInt("seeders", 0),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Check if the TorrServer binary exists and is executable.
     */
    val isBinaryAvailable: Boolean get() = findServerBinary() != null
}

/**
 * Status of the TorrServer process.
 */
enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

/**
 * Information about a torrent in TorrServer.
 */
data class TorrentInfo(
    val hash: String,
    val title: String,
    val size: Long = 0,
    val progress: Float = 0f,
    val status: String = "unknown",
    val seeders: Int = 0,
)
