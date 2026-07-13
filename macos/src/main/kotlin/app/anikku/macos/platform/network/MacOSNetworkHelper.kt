package app.anikku.macos.platform.network

import app.anikku.macos.platform.storage.MacOSStorageProvider
import okhttp3.Cache
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okio.IOException
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.random.Random

/**
 * macOS OkHttp client builder.
 * Replaces the Android NetworkHelper which uses android.content.Context.
 * Uses MacOSStorageProvider for cache directory and MacOSCookieJar for cookies.
 *
 * DoH (DNS-over-HTTPS) providers are the same as the Android version.
 */
class MacOSNetworkHelper(
    private val storageProvider: MacOSStorageProvider,
    private val userAgentProvider: () -> String = { DEFAULT_USER_AGENT },
    private val isDebugBuild: Boolean = false,
) {

    val cookieJar: MacOSCookieJar = MacOSCookieJar(
        cookieFile = File(storageProvider.dataDirectory, "cookies.json"),
    )

    /** Cloudflare bypass interceptor using Chrome CDP for solving JS challenges */
    val cloudflareInterceptor: CloudflareInterceptor = CloudflareInterceptor(cookieJar, userAgentProvider)

    val client: OkHttpClient = buildClient()

    private fun buildClient(
        connectTimeout: Long = 30,
        readTimeout: Long = 60,
        callTimeout: Long = 180,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .callTimeout(callTimeout, TimeUnit.SECONDS)
            .cache(
                Cache(
                    directory = File(storageProvider.dataDirectory, "network_cache"),
                    maxSize = 5L * 1024 * 1024, // 5 MiB
                ),
            )
            .addInterceptor(UserAgentInterceptor(userAgentProvider))
            .addInterceptor(cloudflareInterceptor)
            .addNetworkInterceptor(BrotliInterceptor)

        // Diagnostic logging: always logs errors with full request/response details.
        // In debug builds, also logs a summary for all requests.
        builder.addInterceptor(DiagnosticLoggingInterceptor(isDebugBuild))

        return builder.build()
    }

    /**
     * Download a file with retry and resume capability.
     * Ported from the Android NetworkHelper.
     */
    fun downloadFileWithResume(url: String, outputFile: File, progressListener: ProgressListener) {
        val downloadClient = client.newBuilder()
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
        var attempt = 0

        while (attempt < MAX_RETRY) {
            try {
                val downloadedBytes = outputFile.length()
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$downloadedBytes-")
                    .build()

                var failed = false
                downloadClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 206) {
                        saveResponseToFile(response, outputFile, downloadedBytes)
                        if (response.isSuccessful) return
                    } else {
                        attempt++
                        if (response.code == 416) outputFile.delete()
                        failed = true
                    }
                }
                if (failed) exponentialBackoff(attempt - 1)
            } catch (e: IOException) {
                attempt++
                exponentialBackoff(attempt - 1)
            }
        }
        throw IOException("Max retry attempts ($MAX_RETRY) reached for $url")
    }

    fun setDoHProviders(
        builder: OkHttpClient.Builder,
        provider: Int,
    ): OkHttpClient.Builder {
        return when (provider) {
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
            else -> builder
        }
    }

    private fun saveResponseToFile(response: Response, outputFile: File, startPosition: Long) {
        RandomAccessFile(outputFile, "rw").use { file ->
            file.seek(startPosition)
            response.body?.byteStream()?.use { input ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    file.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun exponentialBackoff(attempt: Int) {
        val delay = 1000L * 2.0.pow(attempt).toLong() + Random.nextLong(0, 1000)
        Thread.sleep(delay.coerceAtMost(32000L))
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
        private const val MAX_RETRY = 5
    }
}

/**
 * Progress listener for file downloads.
 */
fun interface ProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}
