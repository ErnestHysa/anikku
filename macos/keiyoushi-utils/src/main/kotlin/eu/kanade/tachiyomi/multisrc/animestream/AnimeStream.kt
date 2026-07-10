package eu.kanade.tachiyomi.multisrc.animestream

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Minimal JVM-friendly stub of the AnimeStream multisrc.
 *
 * This provides just enough of the AnimeStream interface so that
 * extensions like AnimeKhor (which extends AnimeStream and only
 * overrides getVideoList) can compile on macOS without the full
 * Android-dependent lib-multisrc compilation.
 *
 * The original AnimeStream.kt in lib-multisrc imports AndroidX
 * (PreferenceScreen) and android.util classes that are incompatible
 * with the JVM. This stub replaces all of those with no-ops while
 * preserving the superclass chain that extension source files expect.
 */
abstract class AnimeStream(
    override val lang: String,
    override val name: String,
    override val baseUrl: String,
) : ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val supportsLatest = true

    protected open val preferences: SharedPreferences by getPreferencesLazy()

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun popularAnimeSelector(): String =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun popularAnimeFromElement(element: Element): SAnime =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun popularAnimeNextPageSelector(): String? = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun latestUpdatesSelector(): String =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun latestUpdatesFromElement(element: Element): SAnime =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun latestUpdatesNextPageSelector(): String? = null

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun searchAnimeSelector(): String =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun searchAnimeFromElement(element: Element): SAnime =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun searchAnimeNextPageSelector(): String? = null

    // ============================== Filters ===============================
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime =
        throw UnsupportedOperationException("Stub — override in extension")

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun episodeFromElement(element: Element): SEpisode =
        throw UnsupportedOperationException("Stub — override in extension")

    // ============================== Hosters ===============================
    override fun hosterListSelector(): String =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun hosterFromElement(element: Element): Hoster =
        throw UnsupportedOperationException("Stub — override in extension")

    // ============================ Video Links =============================
    override fun videoListSelector(): String =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun videoFromElement(element: Element): Video =
        throw UnsupportedOperationException("Stub — override in extension")

    override fun videoUrlParse(document: Document): String =
        throw UnsupportedOperationException("Stub — override in extension")

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        /* no-op on JVM */
    }

    // ============================ Video Links =============================
    /**
     * Stub: subclasses override this to extract videos from a URL.
     * Original AnimeStream defines this as protected open suspend fun.
     */
    protected open suspend fun getVideoList(url: String, name: String): List<Video> {
        return emptyList()
    }

    // ============================= Utilities ==============================
    override fun List<Video>.sort(): List<Video> = this
}
