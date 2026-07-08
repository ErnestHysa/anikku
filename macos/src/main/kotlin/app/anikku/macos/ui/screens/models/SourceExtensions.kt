package app.anikku.macos.ui.screens.models

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * Convert an [SAnime] from the source API into the local [AnimeModel] for rendering.
 * Uses the anime URL's hash as a stable ID for navigation.
 */
fun SAnime.toAnimeModel(sourceId: Long = 0L): AnimeModel {
    return AnimeModel(
        id = (url.hashCode()).toLong().let { if (it == 0L) 1L else it },
        title = this.title,
        source = sourceId,
        author = this.author,
        artist = this.artist,
        description = this.description,
        genre = this.getGenres(),
        status = this.status,
        thumbnailUrl = this.thumbnail_url,
        url = this.url,
        favorite = false,
        coverLastModified = 0L,
    )
}

/**
 * Convert an [SEpisode] from the source API into the local [EpisodeModel] for rendering.
 * Uses the episode URL's hash as a stable ID for navigation.
 */
fun SEpisode.toEpisodeModel(animeId: Long = 0L): EpisodeModel {
    return EpisodeModel(
        id = (url.hashCode()).toLong().let { if (it == 0L) 1L else it },
        animeId = animeId,
        name = this.name,
        episodeNumber = this.episode_number.toDouble(),
        url = this.url,
        seen = false,
        bookmark = false,
        dateUpload = this.date_upload,
        scanlator = this.scanlator,
    )
}
