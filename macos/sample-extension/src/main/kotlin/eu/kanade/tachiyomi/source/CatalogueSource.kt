package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.animesource.model.AnimePage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * Stub of CatalogueSource interface for the macOS project.
 *
 * Provides methods for browsing and searching anime catalogs.
 * Extensions compiled against the full source-api implement the fetch-style
 * RxJava methods; the suspend wrappers are the preferred API.
 */
interface CatalogueSource : Source {
    override val lang: String

    // -- Suspend API (preferred) --

    /**
     * Get a page of popular anime from this source.
     */
    suspend fun getPopularAnime(page: Int): AnimePage {
        @Suppress("DEPRECATION")
        return fetchPopularAnime(page).awaitSingle()
    }

    /**
     * Search anime by query.
     */
    suspend fun getSearchAnime(page: Int, query: String): AnimePage {
        @Suppress("DEPRECATION")
        return fetchSearchAnime(page, query).awaitSingle()
    }

    // -- RxJava API (implemented by extensions) --

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularAnime"))
    fun fetchPopularAnime(page: Int): Observable<AnimePage>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchAnime"))
    fun fetchSearchAnime(page: Int, query: String): Observable<AnimePage>
}
