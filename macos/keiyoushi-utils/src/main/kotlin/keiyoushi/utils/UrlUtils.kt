package keiyoushi.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlUtils {

    private val firstHttpsRegex by lazy { Regex("""^.*(?=https?://)""") }

    fun fixUrl(url: String): String? = when {
        url.isEmpty() -> null
        url.startsWith("http") ||
            // Do not fix JSON objects when passed as urls.
            url.startsWith("{\"") -> url
        url.startsWith("//") -> "https:$url"
        else -> url.replaceFirst(firstHttpsRegex, "")
    }

    fun fixUrl(url: String, baseUrl: String): String? {
        val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return null
        return when {
            url.isEmpty() -> null
            url.startsWith("http") ||
                // Do not fix JSON objects when passed as urls.
                url.startsWith("{\"") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                // Will be: http[s]://<domain>/<url>
                baseHttpUrl.newBuilder().encodedPath("/").build().toString()
                    .substringBeforeLast("/") + url
            }
            else -> {
                // Ensure the path starts with '/' for proper baseUrl concatenation.
                // If url is a relative path like "tv/37854/23" (no leading '/'),
                // the path must be normalized to avoid producing malformed URLs
                // when concatenated with baseUrl (e.g. "https://site.comtv/...").
                val normalizedUrl = if (url.startsWith("/")) url else "/$url"
                val basePath = baseHttpUrl.newBuilder().apply {
                    // Remove last path segment to get the parent directory
                    if (pathSize > 0) {
                        removePathSegment(pathSize - 1)
                    }
                    query(null)
                    fragment(null)
                }.build().toString()
                // Ensure basePath ends with / before appending the normalized url
                val safeBase = if (basePath.endsWith("/")) basePath else "$basePath/"
                safeBase + normalizedUrl.drop(1)
            }
        }
    }

    /**
     * Normalize an anime or episode URL to ensure it starts with '/' if it's a relative path.
     * This prevents malformed URLs when concatenated with a base URL (e.g. baseUrl + path).
     *
     * Extensions typically store relative paths (without domain) for anime/episode URLs.
     * These should always start with '/' for correct baseUrl concatenation.
     *
     * @param url The URL or path to normalize
     * @return A normalized URL string that starts with '/' if it's a relative path
     */
    fun normalizeRelativePath(url: String): String {
        if (url.startsWith("http") || url.startsWith("//") || url.startsWith("/")) {
            return url
        }
        return "/$url"
    }
}
