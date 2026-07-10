@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.doodextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

class DoodExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, prefix: String = ""): List<Video> = emptyList()
}
