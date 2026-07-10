@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.vidhideextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

class VidHideExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, prefix: String = ""): List<Video> = emptyList()
}
