@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.streamlareextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

class StreamlareExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, prefix: String = ""): List<Video> = emptyList()
}
