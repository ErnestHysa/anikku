package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private val logger = KotlinLogging.logger {}

/**
 * Helper to install a lenient SSL context that trusts all certificates.
 *
 * Many anime streaming sites use self-signed, expired, or otherwise
 * invalid SSL certificates. On Android, WebView typically handles these
 * more gracefully. On the JVM, OkHttp enforces strict certificate validation
 * which causes `PKIX path building failed` errors.
 *
 * This helper sets the JVM-wide default SSL context to trust all certificates,
 * which fixes SSL errors across all extensions without modifying each one.
 *
 * ## Security Note
 *
 * This is intentionally insecure — it trusts EVERY certificate. It is the
 * same approach used by many web scraping tools and is necessary because
 * anime streaming sites frequently have broken SSL configurations.
 * The alternative (per-site certificate pinning) is impractical for
 * ~55 different extension sources.
 */
object InsecureSSLHelper {

    private var installed = false

    @Synchronized
    fun install() {
        if (installed) {
            logger.debug { "Lenient SSL context already installed" }
            return
        }

        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            SSLContext.setDefault(sslContext)

            // Also set the system property that OkHttp checks
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

            installed = true
            logger.info { "✅ Lenient SSL context installed — trusting all certificates for extension connections" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to install lenient SSL context" }
        }
    }
}
