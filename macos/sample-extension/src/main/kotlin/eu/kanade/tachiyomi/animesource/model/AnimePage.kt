package eu.kanade.tachiyomi.animesource.model

/**
 * Page result from a CatalogueSource fetch operation.
 *
 * @param animeList List of anime returned by the source for this page
 * @param hasNextPage Whether there are more pages available
 */
data class AnimePage(
    val animeList: List<SAnime>,
    val hasNextPage: Boolean,
)
