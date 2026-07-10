@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.filemoonextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class FilemoonExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(
        url: String,
        prefix: String = "Filemoon - ",
        headers: Headers? = null,
    ): List<Video> = emptyList()
}
