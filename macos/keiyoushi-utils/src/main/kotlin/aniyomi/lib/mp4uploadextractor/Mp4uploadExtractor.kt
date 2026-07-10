@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.mp4uploadextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class Mp4uploadExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(
        url: String,
        headers: Headers,
        prefix: String = "",
        suffix: String = "",
    ): List<Video> = emptyList()
}
