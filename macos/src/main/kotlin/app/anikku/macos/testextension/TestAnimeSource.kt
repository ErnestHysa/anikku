package app.anikku.macos.testextension

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.source.CatalogueSource
import rx.Observable
import eu.kanade.tachiyomi.animesource.model.AnimePage

/**
 * Test source for verifying the extension loading and playback pipeline.
 *
 * Returns hardcoded sample anime with a real public-domain video URL
 * so the full pipeline (Browse → Detail → Player → mpv) can be tested.
 *
 * libVersion must be between 12 and 15 (set to 14).
 */
class TestAnimeSource : CatalogueSource {

    override val id: Long = 999001L
    override val name: String = "TestSource"
    override val lang: String = "en"

    // A real playable video URL (public domain Big Buck Bunny)
    private val testVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    private val testThumbnail = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/800px-Big_buck_bunny_poster_big.jpg"

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return SAnime.create().apply {
            url = anime.url
            title = "Big Buck Bunny"
            author = "Blender Foundation"
            artist = "Blender Foundation"
            description = "A large and lovable rabbit deals with three tiny bullies, led by a flying squirrel, who are determined to squelch his happiness."
            genre = "Animation, Short, Comedy"
            status = SAnime.COMPLETED
            thumbnail_url = testThumbnail
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return listOf(
            SEpisode.create().apply {
                url = "/test/episode/1"
                name = "Big Buck Bunny"
                episode_number = 1f
                date_upload = 1700000000000L
                scanlator = "Blender Foundation"
            }
        )
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return listOf(
            Video(
                videoUrl = testVideoUrl,
                videoTitle = "1080p",
                resolution = 1080,
                headers = null,
                preferred = true,
            )
        )
    }

    // RxJava stubs (required by CatalogueSource but unused)
    @Deprecated("Use suspend API")
    override fun fetchPopularAnime(page: Int): Observable<AnimePage> =
        throw UnsupportedOperationException("Use suspend API")

    @Deprecated("Use suspend API")
    override fun fetchSearchAnime(page: Int, query: String): Observable<AnimePage> =
        throw UnsupportedOperationException("Use suspend API")
}
