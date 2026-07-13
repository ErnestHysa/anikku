package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.Interceptor
import okhttp3.Response

private val logger = KotlinLogging.logger {}

/**
 * Cloudflare bypass interceptor using Chrome DevTools Protocol.
 *
 * When a Cloudflare challenge is detected (HTTP 403/503 with Cloudflare server header),
 * this interceptor:
 *
 * 1. Launches headless Chrome via [ChromeCDPClient]
 * 2. Navigates to the protected URL and waits for the JS challenge to resolve
 * 3. Extracts `cf_clearance` and `__cf_bm` cookies
 * 4. Injects cookies into [MacOSCookieJar]
 * 5. Retries the original request with the Cloudflare cookies attached
 *
 * Cookies are cached per-domain in [MacOSCookieJar], so subsequent requests
 * to the same domain don't need to re-solve the challenge (until cookies expire).
 *
 * If Chrome is not installed, falls back to pass-through (original behavior).
 * The `X-CF-Bypass-Attempted` header prevents infinite retry loops.
 */
class CloudflareInterceptor(
    private val cookieJar: MacOSCookieJar,
    private val userAgentProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Not a Cloudflare block — pass through
        if (!isCloudflareBlock(response)) {
            return response
        }

        val url = request.url.toString()
        logger.warn { "🛡 Cloudflare block detected: $url (HTTP ${response.code})" }

        // Prevent infinite retry loops
        if (request.header(X_CF_BYPASS) != null) {
            logger.warn { "Cloudflare bypass already attempted for $url — giving up" }
            return response
        }

        // Close the blocked response body
        response.close()

        // Check if Chrome is available
        if (!ChromeCDPClient.isChromeInstalled) {
            logger.warn { "Chrome not installed — Cloudflare bypass unavailable" }
            return chain.proceed(request.newBuilder().header(X_CF_BYPASS, "1").build())
        }

        // Attempt bypass via headless Chrome
        val userAgent = userAgentProvider()
        val cookies = ChromeCDPClient.fetchCloudflareCookies(url, userAgent)

        if (cookies.isEmpty()) {
            logger.warn { "❌ Cloudflare bypass failed for ${request.url.host}" }
            return chain.proceed(request.newBuilder().header(X_CF_BYPASS, "1").build())
        }

        // Inject cookies into the cookie jar for future requests
        injectCookies(request.url.host, cookies)

        // Retry the request with Cloudflare cookies + bypass-attempted header
        logger.info { "Retrying with Cloudflare cookies for ${request.url.host}" }
        val retryRequest = request.newBuilder()
            .header(X_CF_BYPASS, "1")
            .build()
        val retryResponse = chain.proceed(retryRequest)

        if (isCloudflareBlock(retryResponse)) {
            logger.warn { "Still blocked after bypass for ${request.url.host}" }
        } else {
            logger.info { "✅ Cloudflare bypass successful for ${request.url.host}" }
        }

        return retryResponse
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun isCloudflareBlock(response: Response): Boolean {
        if (response.code !in CLOUDFLARE_CODES) return false

        val server = response.header("Server") ?: ""
        if (server in CLOUDFLARE_SERVERS) return true
        if (response.header("cf-ray") != null) return true

        return try {
            val bodyPeek = response.peekBody(1024).string()
            bodyPeek.contains("cf-browser-verify") ||
                bodyPeek.contains("cf_chl_opt") ||
                bodyPeek.contains("_cf_chl_ctx") ||
                bodyPeek.contains("Checking your browser") ||
                bodyPeek.contains("cf-wrapper") ||
                bodyPeek.contains("challenge-platform")
        } catch (_: Exception) {
            false
        }
    }

    private fun injectCookies(host: String, cookies: Map<String, String>) {
        val cleanHost = host.removePrefix(".")
        val domain = ".$cleanHost"
        for ((name, value) in cookies) {
            try {
                val cookie = okhttp3.Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(domain)
                    .path("/")
                    .httpOnly()
                    .secure()
                    .build()
                val url = okhttp3.HttpUrl.Builder()
                    .scheme("https")
                    .host(cleanHost)
                    .build()
                cookieJar.saveFromResponse(url, listOf(cookie))
                logger.debug { "Injected cookie $name for $cleanHost" }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to inject cookie $name for $cleanHost" }
            }
        }
    }

    companion object {
        private const val X_CF_BYPASS = "X-CF-Bypass-Attempted"
        private val CLOUDFLARE_CODES = setOf(403, 503)
        private val CLOUDFLARE_SERVERS = setOf("cloudflare-nginx", "cloudflare")
    }
}
