package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

actual open class NetworkHelper(
    private val preferences: NetworkPreferences,
    val isDebugBuild: Boolean,
    // macOS/JVM extensions: inject CookieJar and interceptors for Cloudflare bypass
    // and diagnostic logging. Extension HTTP calls go through this client.
    private val cookieJar: CookieJar? = null,
    private val extraInterceptors: List<Interceptor> = emptyList(),
) {

    actual val client: OkHttpClient =
        clientWithTimeOut()

    actual fun clientWithTimeOut(
        connectTimeout: Long,
        readTimeout: Long,
        callTimeout: Long,
    ): OkHttpClient = run {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .callTimeout(callTimeout, TimeUnit.SECONDS)
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor)

        // macOS/JVM: inject shared cookie jar so Cloudflare bypass cookies
        // obtained by ChromeCDPClient flow through to extension HTTP calls
        if (cookieJar != null) {
            builder.cookieJar(cookieJar)
        }

        // macOS/JVM: inject Cloudflare bypass + diagnostic logging interceptors
        extraInterceptors.forEach { builder.addInterceptor(it) }

        if (isDebugBuild) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        when (preferences.dohProvider().get()) {
            PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
            PREF_DOH_GOOGLE -> builder.dohGoogle()
            PREF_DOH_ADGUARD -> builder.dohAdGuard()
            PREF_DOH_QUAD9 -> builder.dohQuad9()
            PREF_DOH_ALIDNS -> builder.dohAliDNS()
            PREF_DOH_DNSPOD -> builder.dohDNSPod()
            PREF_DOH_360 -> builder.doh360()
            PREF_DOH_QUAD101 -> builder.dohQuad101()
            PREF_DOH_MULLVAD -> builder.dohMullvad()
            PREF_DOH_CONTROLD -> builder.dohControlD()
            PREF_DOH_NJALLA -> builder.dohNajalla()
            PREF_DOH_SHECAN -> builder.dohShecan()
            PREF_DOH_LIBREDNS -> builder.dohLibreDNS()
        }

        builder.build()
    }

    actual fun defaultUserAgentProvider() = preferences.defaultUserAgent().get().trim()

    @Deprecated("The regular client handles Cloudflare by default", ReplaceWith("client"))
    @Suppress("UNUSED")
    open val cloudflareClient: OkHttpClient
        get() = client
}
