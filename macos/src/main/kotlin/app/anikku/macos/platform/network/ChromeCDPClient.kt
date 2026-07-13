package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * Cloudflare bypass via Chrome DevTools Protocol (CDP).
 *
 * Launches the user's installed Chrome in headless mode, navigates to a
 * Cloudflare-protected URL, waits for the JavaScript challenge to resolve,
 * and extracts the `cf_clearance` and `__cf_bm` cookies.
 *
 * ## Why CDP instead of Selenium/Playwright/FlareSolverr
 *
 * - **Zero new dependencies** — uses the user's installed Chrome + OkHttp WebSocket
 * - **Lightweight** — no 100MB+ browser bundles, no Python, no Docker
 * - **Reliable** — real Chrome with native TLS + JS = passes all Cloudflare checks
 * - **Fast after first use** — cookies cached per domain in MacOSCookieJar
 *
 * ## Thread safety
 *
 * Only one bypass attempt runs at a time (synchronized on the object).
 * Concurrent bypasses from multiple extensions queue up — only the first
 * actually launches Chrome, subsequent ones wait and reuse cached cookies.
 */
object ChromeCDPClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Is Chrome installed at the standard macOS path? */
    val isChromeInstalled: Boolean by lazy {
        chromeExecutable().isFile
    }

    /**
     * Fetch Cloudflare bypass cookies for a URL.
     * Thread-safe — only one bypass runs at a time.
     *
     * @param url The Cloudflare-protected URL to visit
     * @param userAgent The User-Agent to use
     * @param timeoutSeconds Max time to wait for challenge resolution
     * @return Map of cookie name → value, or empty map on failure
     */
    @Synchronized
    fun fetchCloudflareCookies(
        url: String,
        userAgent: String,
        timeoutSeconds: Long = 30,
    ): Map<String, String> {
        if (!isChromeInstalled) {
            logger.warn { "Chrome not found — Cloudflare bypass unavailable" }
            return emptyMap()
        }

        var process: Process? = null
        try {
            // Launch Chrome with remote debugging, parse port from stderr
            val (chromeProcess, debugPort) = launchChromeWithPort()
            process = chromeProcess

            // Get the WebSocket debugger URL
            val wsUrl = getDebuggerUrl(debugPort)
            if (wsUrl == null) {
                logger.warn { "Failed to get Chrome DevTools URL on port $debugPort" }
                return emptyMap()
            }

            // Navigate to URL and wait for Cloudflare challenge to resolve
            val cookies = navigateAndWait(wsUrl, url, timeoutSeconds)
            if (cookies.isNotEmpty()) {
                logger.info { "✅ Cloudflare bypass succeeded — got ${cookies.size} cookie(s)" }
            } else {
                logger.warn { "No Cloudflare cookies found — challenge may have failed" }
            }

            return cookies
        } catch (e: Exception) {
            logger.error(e) { "Cloudflare bypass failed" }
            return emptyMap()
        } finally {
            process?.let {
                it.destroyForcibly()
                try { it.waitFor(2, TimeUnit.SECONDS) } catch (_: Exception) {}
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────

    private fun chromeExecutable(): File {
        return File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
    }

    /**
     * Launch Chrome with `--remote-debugging-port=0` and parse the assigned
     * port from stderr. Returns a single Chrome instance — no double launch.
     */
    private fun launchChromeWithPort(): Pair<Process, Int> {
        val builder = ProcessBuilder(
            chromeExecutable().absolutePath,
            "--remote-debugging-port=0",
            "--headless=new",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--disable-extensions",
            "--disable-background-networking",
            "--disable-sync",
            "--no-sandbox",
            "about:blank",
        )
        builder.environment()["DISPLAY"] = ""
        val process = builder.start()

        // Parse port from stderr: "DevTools listening on ws://127.0.0.1:PORT/devtools/browser/HASH"
        val portRef = AtomicInteger(-1)
        val stderrThread = Thread({
            try {
                val reader = process.errorStream.bufferedReader()
                var line: String? = reader.readLine()
                while (line != null && portRef.get() < 0) {
                    if (line.contains("DevTools listening on ws://")) {
                        Regex("""ws://127\.0\.0\.1:(\d+)""").find(line)
                            ?.groupValues?.get(1)?.toIntOrNull()?.let { portRef.set(it) }
                    }
                    line = reader.readLine()
                }
            } catch (_: Exception) {}
        }, "chrome-stderr-reader")
        stderrThread.isDaemon = true
        stderrThread.start()

        // Wait for the port with a timeout
        val startTime = System.currentTimeMillis()
        val timeoutMs = 10000L
        while (portRef.get() < 0 && System.currentTimeMillis() - startTime < timeoutMs) {
            Thread.sleep(100)
        }
        stderrThread.interrupt()

        val port = portRef.get()
        if (port < 0) {
            process.destroyForcibly()
            throw IllegalStateException("Failed to get Chrome DevTools port within ${timeoutMs}ms")
        }

        // Give Chrome a moment to finish initializing
        Thread.sleep(300)

        return Pair(process, port)
    }

    private fun getDebuggerUrl(port: Int): String? {
        return try {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/json/version")
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val obj = json.parseToJsonElement(body).jsonObject
            obj["webSocketDebuggerUrl"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            logger.error(e) { "Failed to get DevTools WebSocket URL on port $port" }
            null
        }
    }

    /**
     * Navigate to a URL via CDP WebSocket, wait for page load (Cloudflare
     * JS challenge included), then extract cookies via Network.getCookies.
     */
    private fun navigateAndWait(
        wsUrl: String,
        targetUrl: String,
        timeoutSeconds: Long,
    ): Map<String, String> {
        val latch = CountDownLatch(1)
        val cookies = ConcurrentHashMap<String, String>()
        var messageId = 0

        val ws = httpClient.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    messageId++
                    webSocket.send("""{"id":$messageId,"method":"Page.enable"}""")
                    messageId++
                    webSocket.send(
                        """{"id":$messageId,"method":"Page.navigate","params":{"url":"$targetUrl"}}"""
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = json.parseToJsonElement(text).jsonObject
                        val method = msg["method"]?.jsonPrimitive?.content

                        // Page fully loaded → Cloudflare JS challenge resolved.
                        // Schedule cookie fetch after a brief delay to let post-load
                        // CF JavaScript finish executing.
                        if (method == "Page.loadEventFired") {
                            scheduleCookieFetch(webSocket, targetUrl, delayMs = 1500)
                        }

                        // Process Network.getCookies response
                        val result = msg["result"]
                        val msgId = msg["id"]
                        if (result != null && msgId != null) {
                            val cookiesArray = result.jsonObject["cookies"]?.jsonArray
                            if (cookiesArray != null && cookiesArray.isNotEmpty()) {
                                for (cookieElement in cookiesArray) {
                                    val cookie = cookieElement.jsonObject
                                    val name = cookie["name"]?.jsonPrimitive?.content ?: continue
                                    val value = cookie["value"]?.jsonPrimitive?.content ?: continue
                                    if (name in CLOUDFLARE_COOKIE_NAMES) {
                                        cookies[name] = value
                                    }
                                }
                                latch.countDown()
                                webSocket.close(1000, "Cookies extracted")
                            }
                        }

                        // Page load error — still try to get cookies (CF may have set them)
                        if (method == "Page.frameStoppedLoading") {
                            scheduleCookieFetch(webSocket, targetUrl, delayMs = 1000)
                        }
                    } catch (_: Exception) {
                        // Malformed CDP message — ignore
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    logger.warn(t) { "CDP WebSocket connection failed" }
                    latch.countDown()
                }
            },
        )

        val success = latch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (!success) {
            logger.warn { "Timed out waiting for Cloudflare challenge after ${timeoutSeconds}s" }
            ws.close(1000, "Timeout")
        }

        return cookies.toMap()
    }

    /**
     * Schedule a [Network.getCookies] request after a delay, without blocking
     * the WebSocket dispatch thread.
     */
    private fun scheduleCookieFetch(
        webSocket: WebSocket,
        targetUrl: String,
        delayMs: Long,
    ) {
        // Use an incremental ID tracked outside the WebSocketListener
        // to avoid closure issues with the local "messageId" variable.
        val thread = Thread({
            Thread.sleep(delayMs)
            val id = cookieFetchId.incrementAndGet()
            webSocket.send(
                """{"id":$id,"method":"Network.getCookies","params":{"urls":["$targetUrl"]}}"""
            )
        }, "chrome-cdp-cookie-fetch")
        thread.isDaemon = true
        thread.start()
    }

    /** Monotonically increasing message ID for scheduled cookie fetch requests. */
    private val cookieFetchId = AtomicInteger(1000)

    private val CLOUDFLARE_COOKIE_NAMES = setOf(
        "cf_clearance",
        "__cf_bm",
        "cf_chl_2",
        "cf_chl_3",
        "cf_chl_rc_ni",
        "cf_chl_rc_m",
    )
}
