package app.anikku.macos.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.ui.AnikkuScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey

/**
 * Stats screen — Phase 5.11.
 *
 * Shows anime watching statistics computed from real data:
 * - Total anime in library
 * - Total episodes watched
 * - Estimated watch time
 * - Completed / ongoing breakdown
 * - Top genres
 */
class StatsScreen : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val libraryRepo = LocalLibraryRepository.current
        val historyRepo = LocalHistoryRepository.current

        val libraryEntries = remember { libraryRepo.getAll() }
        val historyEntries = remember { historyRepo.getAll() }

        // Compute stats from real data
        val stats = remember(libraryEntries, historyEntries) {
            val totalAnime = libraryEntries.size
            val totalEpisodes = historyEntries.size
            val totalWatchTimeMinutes = historyEntries.sumOf { it.watchDuration } / 60_000L

            val completedSeries = libraryEntries.count { it.status == 2 }
            val ongoingSeries = libraryEntries.count { it.status == 1 }

            // Aggregate genres from library entries, sorted by frequency (top 6)
            val genreCounts: Map<String, Int> = libraryEntries
                .mapNotNull { it.genre }
                .flatten()
                .groupingBy { it }
                .eachCount()
            val topGenres = genreCounts.entries
                .sortedByDescending { it.value }
                .take(6)
                .map { it.key to it.value }

            StatsData(
                totalAnime = totalAnime,
                totalEpisodes = totalEpisodes,
                totalWatchTimeMinutes = totalWatchTimeMinutes,
                completedSeries = completedSeries,
                ongoingSeries = ongoingSeries,
                topGenres = topGenres,
            )
        }

        StatsContent(stats = stats)
    }
}

private data class StatsData(
    val totalAnime: Int,
    val totalEpisodes: Int,
    val totalWatchTimeMinutes: Long,
    val completedSeries: Int,
    val ongoingSeries: Int,
    val topGenres: List<Pair<String, Int>>,
)

@Composable
private fun StatsContent(stats: StatsData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Overview cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    icon = Icons.Outlined.LibraryBooks,
                    label = "Library",
                    value = "${stats.totalAnime}",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = Icons.Outlined.PlayCircle,
                    label = "Episodes",
                    value = "${stats.totalEpisodes}",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = Icons.Outlined.AccessTime,
                    label = "Watch Time",
                    value = formatWatchTime(stats.totalWatchTimeMinutes),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Status breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))

                    StatusRow(label = "Completed", count = stats.completedSeries, total = stats.totalAnime)
                    Spacer(Modifier.height(8.dp))
                    StatusRow(label = "Ongoing", count = stats.ongoingSeries, total = stats.totalAnime)
                }
            }
        }

        // Top genres
        if (stats.topGenres.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Top Genres",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))

                        stats.topGenres.forEach { (genre, count) ->
                            GenreRow(genre = genre, count = count, maxCount = stats.topGenres.first().second)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        // Show a hint if there's no data yet
        if (stats.totalAnime == 0 && stats.totalEpisodes == 0) {
            item {
                Text(
                    text = "Start adding anime to your library and watching episodes to see statistics here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            item {
                Text(
                    text = "Stats are computed from your library and watch history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, count: Int, total: Int) {
    val fraction = if (total > 0) count.toFloat() / total else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(100.dp),
        )
        androidx.compose.material3.LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GenreRow(genre: String, count: Int, maxCount: Int) {
    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = genre,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(100.dp),
        )
        androidx.compose.material3.LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count anime",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatWatchTime(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours >= 24 -> {
            val days = hours / 24
            val remainingHours = hours % 24
            "${days}d ${remainingHours}h"
        }
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}
