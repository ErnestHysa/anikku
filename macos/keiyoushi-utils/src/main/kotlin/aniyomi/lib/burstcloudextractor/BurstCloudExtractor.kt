@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.burstcloudextractor

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class BurstCloudExtractor(private val client: OkHttpClient) {
    fun videoFromUrl(url: String, headers: Headers, name: String = "BurstCloud", prefix: String = ""): List<Video> = emptyList()
}
