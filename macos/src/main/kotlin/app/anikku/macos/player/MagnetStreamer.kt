package app.anikku.macos.player

import app.anikku.macos.platform.logging.CrashReporter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Result of attempting to start a magnet stream.
 */
sealed class MagnetStreamResult {
    /** Success — the stream is being served at this local HTTP URL. */
    data class Success(val httpUrl: String, val process: Process) : MagnetStreamResult()

    /** Failure — webtorrent-cli is not available or streaming failed. */
    data class Failure(val message: String) : MagnetStreamResult()
}

/**
 * Streams magnet links via webtorrent-cli for playback in mpv.
 *
 * Architecture:
 *   magnet:?xt=urn:btih:...  ─→  webtorrent-cli  ─→  local HTTP server
 *                                                       │
 *                                                       ▼
 *                                                   mpv plays
 *                                                  http://localhost:xxxx/0
 *
 * Requirements:
 *   - Node.js with webtorrent-cli installed:
 *       npm install -g webtorrent-cli
 *     Or use npx (auto-downloads if needed):
 *       npx webtorrent-cli <magnet>
 *
 * Flow:
 *   [PlayerViewModel] detects magnet:// URL
 *         │
 *         ▼
 *   [MagnetStreamer.startStreaming(magnetUrl)]
 *         │
 *         ├── Spawns: npx --yes webtorrent-cli "<magnet>" -p 0
 *         │   (-p 0 = pick any available port)
 *         │
 *         ├── Parses stdout for "http://localhost:PORT/INDEX"
 *         │
 *         ├── Returns Success(httpUrl, process)
 *         │   └── PlayerViewModel loads httpUrl into mpv via loadfile
 *         │
 *         └── On shutdown / playback end:
 *             └── Process.destroy() to kill webtorrent
 */
object MagnetStreamer {

    private const val TIMEOUT_SECONDS = 60L

    /**
     * Check if webtorrent-cli is available on this system.
     *
     * Only checks for a global install via `which webtorrent`.
     * The `npx` fallback is NOT used here because it would download the
     * entire npm package just for an availability check, which is very slow.
     * If not globally installed, `npx` will be used automatically when
     * [startStreaming] is called (which handles the download then).
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val which = ProcessBuilder("which", "webtorrent")
                .redirectErrorStream(true)
                .start()
            which.waitFor(5, TimeUnit.SECONDS) && which.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Start streaming a magnet link via webtorrent-cli.
     *
     * This spawns a webtorrent HTTP server for the magnet link and returns
     * the local URL (e.g., http://localhost:50981/0) that serves the first
     * media file in the torrent.
     *
     * @param magnetUrl The magnet:?xt=urn:btih:... link
     * @return [MagnetStreamResult.Success] with the HTTP URL and managed process,
     *         or [MagnetStreamResult.Failure] with an error message.
     */
    suspend fun startStreaming(magnetUrl: String): MagnetStreamResult = withContext(Dispatchers.IO) {
        logger.info { "🧲 MAGNET_STREAM: Starting torrent stream: ${magnetUrl.take(80)}..." }
        CrashReporter.logEvent("Magnet stream start", "url=${magnetUrl.take(80)}")

        try {
            // Spawn webtorrent-cli via npx (auto-downloads if not installed)
            // -p 0 = pick any available port (avoids port conflicts)
            val processBuilder = ProcessBuilder(
                "npx", "--yes", "webtorrent-cli",
                magnetUrl,
                "-p", "0",
            )
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val reader = process.inputStream.bufferedReader()

            // Read stdout line by line until we find the HTTP server URL
            val httpUrlPattern = Regex("http://localhost:\\d+/\\d+")
            var serverUrl: String? = null
            val startTime = System.currentTimeMillis()

            while (true) {
                // Timeout check
                if (System.currentTimeMillis() - startTime > TIMEOUT_SECONDS * 1000) {
                    process.destroy()
                    val msg = "Magnet stream timed out after ${TIMEOUT_SECONDS}s"
                    logger.warn { "🧲 MAGNET_STREAM: $msg" }
                    CrashReporter.logEvent("Magnet timeout", msg)
                    return@withContext MagnetStreamResult.Failure(msg)
                }

                val line = reader.readLine() ?: break
                logger.debug { "🧲 MAGNET_STREAM: $line" }

                // Look for the local HTTP server URL
                val match = httpUrlPattern.find(line)
                if (match != null) {
                    serverUrl = match.value
                    logger.info { "🧲 MAGNET_STREAM: Found server URL: $serverUrl" }
                    break
                }

                // Check for common errors in output
                val lowerLine = line.lowercase()
                when {
                    "error" in lowerLine && ("not found" in lowerLine || "cant find" in lowerLine) -> {
                        process.destroy()
                        val msg = "webtorrent-cli not found. Install: npm install -g webtorrent-cli"
                        logger.warn { "🧲 MAGNET_STREAM: $msg" }
                        CrashReporter.logEvent("Magnet error", msg)
                        return@withContext MagnetStreamResult.Failure(msg)
                    }
                    "no peers" in lowerLine && "no tmp" in lowerLine -> {
                        // Still trying to connect — keep waiting
                        logger.debug { "🧲 MAGNET_STREAM: Waiting for peers..." }
                    }
                }
            }

            if (serverUrl == null) {
                process.destroy()
                val msg = "Could not find HTTP server URL in webtorrent output"
                logger.warn { "🧲 MAGNET_STREAM: $msg" }
                CrashReporter.logEvent("Magnet error", msg)
                return@withContext MagnetStreamResult.Failure(msg)
            }

            logger.info { "🧲 MAGNET_STREAM: Success! Stream ready at $serverUrl" }
            CrashReporter.logEvent("Magnet success", "url=$serverUrl")
            MagnetStreamResult.Success(serverUrl, process)

        } catch (e: Exception) {
            val msg = "Magnet stream failed: ${e.message ?: "Unknown error"}"
            logger.error(e) { "🧲 MAGNET_STREAM: $msg" }
            CrashReporter.logError("MagnetStream", msg, e)
            MagnetStreamResult.Failure(msg)
        }
    }

    /**
     * Stop a running webtorrent process and clean up resources.
     */
    fun stopStreaming(result: MagnetStreamResult.Success) {
        try {
            result.process.destroy()
            // Force kill if it doesn't stop within 3 seconds
            if (!result.process.waitFor(3, TimeUnit.SECONDS)) {
                result.process.destroyForcibly()
            }
            logger.info { "🧲 MAGNET_STREAM: Process stopped" }
        } catch (e: Exception) {
            logger.warn(e) { "🧲 MAGNET_STREAM: Error stopping process" }
        }
    }
}
