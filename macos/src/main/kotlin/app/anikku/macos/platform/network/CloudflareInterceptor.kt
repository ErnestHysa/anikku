package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.Interceptor
import okhttp3.Response

private val logger = KotlinLogging.logger {}

/**
 * macOS stub for the Cloudflare bypass interceptor.
 *
 * The Android version uses a hidden WebView to load Cloudflare challenge pages
 * and extract the `cf_clearance` cookie. On macOS, we don't have Android WebView
 * available. This interceptor will be replaced with a system-browser-based bypass
 * in Phase 8 (WebView Replacement).
 *
 * For now, this is a no-op pass-through interceptor. Sources behind Cloudflare
 * protection will fail with 403/503 errors. Users can work around this by:
 * 1. Using the system browser to manually solve the challenge
 * 2. Waiting for Phase 8's proper Cloudflare bypass implementation
 */
class CloudflareInterceptor(
    private val cookieJar: MacOSCookieJar,
    private val userAgentProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code in CLOUDFLARE_CODES &&
            response.header("Server") in CLOUDFLARE_SERVERS
        ) {
            logger.warn {
                "Cloudflare challenge detected for ${request.url}. " +
                    "WebView-based bypass not available on macOS — will be implemented in Phase 8."
            }
        }

        return response
    }

    companion object {
        private val CLOUDFLARE_CODES = setOf(403, 503)
        private val CLOUDFLARE_SERVERS = setOf("cloudflare-nginx", "cloudflare")
    }
}
