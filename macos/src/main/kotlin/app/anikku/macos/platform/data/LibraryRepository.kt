package app.anikku.macos.platform.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JSON-backed repository for the user's anime library (favorites).
 *
 * Stores anime the user has favorited/added to their library.
 * Data file: ~/Library/Application Support/Anikku/data/library.json
 */
class LibraryRepository(private val dataDir: File) {

    private val libraryFile = File(dataDir, "library.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class LibraryEntry(
        val animeId: Long,
        val title: String,
        val sourceId: Long = 0L,
        val url: String? = null,
        val thumbnailUrl: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Int = 0,
        val addedAt: Long = System.currentTimeMillis(),
        val lastUpdatedAt: Long = System.currentTimeMillis(),
    )

    private var entries: MutableList<LibraryEntry> = loadFromFile()

    fun getAll(): List<LibraryEntry> = entries.toList()

    fun get(animeId: Long): LibraryEntry? = entries.find { it.animeId == animeId }

    fun isInLibrary(animeId: Long): Boolean = entries.any { it.animeId == animeId }

    fun add(entry: LibraryEntry) {
        val existing = entries.indexOfFirst { it.animeId == entry.animeId }
        if (existing >= 0) {
            entries[existing] = entry.copy(
                lastUpdatedAt = System.currentTimeMillis(),
            )
        } else {
            entries.add(entry)
        }
        saveToFile()
    }

    fun remove(animeId: Long): Boolean {
        val removed = entries.removeAll { it.animeId == animeId }
        if (removed) saveToFile()
        return removed
    }

    fun toggle(entry: LibraryEntry): Boolean {
        return if (isInLibrary(entry.animeId)) {
            remove(entry.animeId)
            false
        } else {
            add(entry)
            true
        }
    }

    fun count(): Int = entries.size

    private fun loadFromFile(): MutableList<LibraryEntry> {
        if (!libraryFile.exists()) return mutableListOf()
        return try {
            val list = json.decodeFromString<LibraryList>(libraryFile.readText())
            list.entries.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveToFile() {
        libraryFile.parentFile?.mkdirs()
        libraryFile.writeText(json.encodeToString(LibraryList(entries)))
    }

    @Serializable
    private data class LibraryList(val entries: List<LibraryEntry>)
}
