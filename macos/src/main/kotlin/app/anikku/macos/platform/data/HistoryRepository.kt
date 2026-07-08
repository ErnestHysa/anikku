package app.anikku.macos.platform.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JSON-backed repository for the user's episode watch history.
 *
 * Records episodes the user has watched, including duration and timestamp.
 * Data file: ~/Library/Application Support/Anikku/data/history.json
 */
class HistoryRepository(private val dataDir: File) {

    private val historyFile = File(dataDir, "history.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class HistoryEntry(
        val animeId: Long,
        val episodeId: Long,
        val animeTitle: String = "",
        val episodeName: String = "",
        val episodeNumber: Double = 0.0,
        val sourceId: Long = 0L,
        val episodeUrl: String? = null,
        val seenAt: Long = System.currentTimeMillis(),
        val watchDuration: Long = 0L,
    )

    private var entries: MutableList<HistoryEntry> = loadFromFile()

    fun getAll(): List<HistoryEntry> = entries.toList()

    fun getLatest(): HistoryEntry? = entries.maxByOrNull { it.seenAt }

    fun add(entry: HistoryEntry) {
        // Remove duplicate entry for same episode if exists (replace with latest)
        entries.removeAll { it.episodeId == entry.episodeId && it.animeId == entry.animeId }
        entries.add(entry)
        // Keep only last 500 entries to prevent unbounded growth
        if (entries.size > 500) {
            entries = entries.sortedByDescending { it.seenAt }.take(500).toMutableList()
        }
        saveToFile()
    }

    fun clearAll() {
        entries.clear()
        saveToFile()
    }

    fun count(): Int = entries.size

    fun getForAnime(animeId: Long): List<HistoryEntry> =
        entries.filter { it.animeId == animeId }.sortedByDescending { it.seenAt }

    private fun loadFromFile(): MutableList<HistoryEntry> {
        if (!historyFile.exists()) return mutableListOf()
        return try {
            val list = json.decodeFromString<HistoryList>(historyFile.readText())
            list.entries.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveToFile() {
        historyFile.parentFile?.mkdirs()
        historyFile.writeText(json.encodeToString(HistoryList(entries)))
    }

    @Serializable
    private data class HistoryList(val entries: List<HistoryEntry>)
}
