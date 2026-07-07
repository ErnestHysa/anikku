package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * macOS stub of AnimeSource interface.
 * Mirrors the source-api AnimeSource contract.
 */
interface AnimeSource {

    val id: Long
    val name: String
    val lang: String get() = ""

    suspend fun getAnimeDetails(anime: SAnime): SAnime {
        @Suppress("DEPRECATION")
        return fetchAnimeDetails(anime).awaitSingle()
    }

    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        @Suppress("DEPRECATION")
        return fetchEpisodeList(anime).awaitSingle()
    }

    suspend fun getHosterList(episode: SEpisode): List<Hoster> = throw IllegalStateException("Not used")

    suspend fun getVideoList(hoster: Hoster): List<Video> = throw IllegalStateException("Not used")

    suspend fun getVideoList(episode: SEpisode): List<Video> {
        @Suppress("DEPRECATION")
        return fetchVideoList(episode).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getAnimeDetails"))
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> =
        throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getEpisodeList"))
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> =
        throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getVideoList"))
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>> =
        throw IllegalStateException("Not used")

    // KMK -->
    suspend fun getRelatedAnimeList(
        anime: SAnime,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ): Unit = getRelatedMangaList(anime, exceptionHandler, pushResults)

    suspend fun getRelatedMangaList(
        anime: SAnime,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ): Unit = throw UnsupportedOperationException()
    // KMK <--
}
