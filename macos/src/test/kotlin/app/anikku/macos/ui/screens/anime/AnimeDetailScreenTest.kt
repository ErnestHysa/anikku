package app.anikku.macos.ui.screens.anime

import app.anikku.macos.ui.screens.models.MockData
import org.junit.Test

/**
 * Unit tests for [AnimeDetailScreen] and related mock data.
 *
 * Verifies screen construction, mock data lookup, and episode data integrity.
 * Compose UI rendering tests are left for when Voyager Navigator test
 * infrastructure is available in Compose Desktop.
 */
class AnimeDetailScreenTest {

    @Test
    fun `screen can be constructed with anime ID`() {
        val screen = AnimeDetailScreen(animeId = 1L)
        assert(screen.animeId == 1L)
    }

    @Test
    fun `mock data lookup finds anime by ID`() {
        val anime = MockData.sampleAnime.find { it.id == 1L }
        assert(anime != null)
        assert(anime!!.title == "Attack on Titan")
    }

    @Test
    fun `mock data returns null for unknown ID`() {
        val anime = MockData.sampleAnime.find { it.id == 999L }
        assert(anime == null) { "Unknown ID should return null" }
    }

    @Test
    fun `episodes filtered by anime ID`() {
        val anime1Episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        assert(anime1Episodes.size == 8) { "Anime 1 should have 8 episodes" }
    }

    @Test
    fun `no episodes for unknown anime ID`() {
        val episodes = MockData.sampleEpisodes.filter { it.animeId == 999L }
        assert(episodes.isEmpty())
    }

    @Test
    fun `episodes have correct seen distribution`() {
        val anime1Episodes = MockData.sampleEpisodes.filter { it.animeId == 1L }
        val seen = anime1Episodes.count { it.seen }
        val unseen = anime1Episodes.count { !it.seen }

        assert(seen == 2) { "Should have 2 seen episodes" }
        assert(unseen == 6) { "Should have 6 unseen episodes" }
    }

    @Test
    fun `all episodes have non-blank names`() {
        val empty = MockData.sampleEpisodes.filter { it.name.isBlank() }
        assert(empty.isEmpty()) { "All episodes should have names" }
    }

    @Test
    fun `all episodes have valid episode numbers`() {
        val invalid = MockData.sampleEpisodes.filter { it.episodeNumber <= 0 }
        assert(invalid.isEmpty()) { "All episodes should have positive episode numbers" }
    }

    @Test
    fun `episodes are ordered by number`() {
        val numbers = MockData.sampleEpisodes
            .filter { it.animeId == 1L }
            .map { it.episodeNumber }
        for (i in 0 until numbers.size - 1) {
            assert(numbers[i] < numbers[i + 1]) {
                "Episodes should be ordered by number ascending"
            }
        }
    }
}
