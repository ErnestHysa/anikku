@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.streamwishextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class StreamWishExtractor(private val client: OkHttpClient, private val headers: Headers) {
    suspend fun videosFromUrl(url: String, prefix: String): List<Video> =
        videosFromUrl(url) { "$prefix - $it" }

    suspend fun videosFromUrl(
        url: String,
        videoNameGen: (String) -> String = { quality -> "StreamWish - $quality" },
    ): List<Video> = emptyList()
}
