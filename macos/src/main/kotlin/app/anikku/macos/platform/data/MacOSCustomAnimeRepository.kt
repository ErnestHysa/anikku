package app.anikku.macos.platform.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * macOS-specific custom anime info repository.
 *
 * Stores user-defined custom anime metadata edits as JSON.
 * Data file: {dataDirectory}/edits.json
 *
 * TODO Phase 2+: Implement tachiyomi.domain.anime.repository.CustomAnimeRepository
 * when shared modules are integrated via desktopMain source sets.
 */
class MacOSCustomAnimeRepository(dataDir: File) {

    private val editJson = File(dataDir, "edits.json")
    private val customAnimeMap: MutableMap<Long, CustomAnimeEntry> = loadFromFile()

    fun get(animeId: Long): CustomAnimeEntry? = customAnimeMap[animeId]

    fun set(animeId: Long, title: String? = null, author: String? = null,
            artist: String? = null, thumbnailUrl: String? = null,
            description: String? = null, genre: List<String>? = null, status: Long? = null) {
        val existing = customAnimeMap[animeId]
        val entry = CustomAnimeEntry(
            id = animeId,
            title = title ?: existing?.title,
            author = author ?: existing?.author,
            artist = artist ?: existing?.artist,
            thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
            description = description ?: existing?.description,
            genre = genre ?: existing?.genre,
            status = status ?: existing?.status,
        )
        customAnimeMap[animeId] = entry
        saveToFile()
    }

    fun remove(animeId: Long) {
        customAnimeMap.remove(animeId)
        saveToFile()
    }

    private fun loadFromFile(): MutableMap<Long, CustomAnimeEntry> {
        if (!editJson.exists() || !editJson.isFile) return mutableMapOf()
        return try {
            val list = Json.decodeFromString<AnimeList>(editJson.readText())
            list.animes?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveToFile() {
        if (customAnimeMap.isNotEmpty()) {
            editJson.parentFile?.mkdirs()
            editJson.writeText(Json.encodeToString(AnimeList(customAnimeMap.values.toList())))
        }
    }

    @Serializable
    data class CustomAnimeEntry(
        val id: Long,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
    )

    @Serializable
    private data class AnimeList(val animes: List<CustomAnimeEntry>? = null)
}
