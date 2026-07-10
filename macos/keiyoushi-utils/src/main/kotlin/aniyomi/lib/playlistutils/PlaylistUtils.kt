@file:Suppress("UNUSED_PARAMETER")

package aniyomi.lib.playlistutils

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import keiyoushi.utils.commonEmptyHeaders
import okhttp3.Headers
import okhttp3.OkHttpClient

/**
 * Stub for PlaylistUtils — resolves m3u8/mpd playlists and subtitles.
 */
class PlaylistUtils(
    private val client: OkHttpClient,
    private val headers: Headers = commonEmptyHeaders,
) {
    fun extractFromHls(
        playlistUrl: String,
        referer: String = "",
        videoNameGen: (String) -> String = { quality -> quality },
        subtitleList: List<Track> = emptyList(),
    ): List<Video> = emptyList()

    /**
     * Overload with named params (masterHeaders, videoHeaders) used by Anikage etc.
     */
    fun extractFromHls(
        playlistUrl: String,
        referer: String = playlistUrl,
        masterHeaders: Headers,
        videoHeaders: Headers,
        videoNameGen: (String) -> String = { quality -> quality },
        subtitleList: List<Track> = emptyList(),
    ): List<Video> = emptyList()

    fun extractFromDash(
        mpdUrl: String,
        videoNameGen: (String) -> String = { quality -> quality },
        subtitleList: List<Track> = emptyList(),
        referer: String = "",
    ): List<Video> = emptyList()

    fun extractM3u8(
        playlistUrl: String,
        videoNameGen: (String) -> String = { it },
        subtitleList: List<Track> = emptyList(),
    ): List<Video> = emptyList()

    /**
     * Dummy — does nothing in stub mode.
     */
    fun fixSubtitles(tracks: List<Track>): List<Track> = tracks
}

