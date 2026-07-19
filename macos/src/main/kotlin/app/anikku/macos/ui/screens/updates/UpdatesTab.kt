package app.anikku.macos.ui.screens.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions

/**
 * Updates screen tab — Phase 5.
 *
 * Shows recent episode updates from tracked anime.
 * Reads library entries and checks sources for new episodes.
 * Falls back gracefully when sources or library are unavailable.
 */
object UpdatesTab : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val libraryRepo = LocalLibraryRepository.current
        val historyRepo = LocalHistoryRepository.current
        val extensionManager = LocalExtensionManager.current

        val libraryEntries = remember { libraryRepo.getAll() }
        val historyEntries = remember { historyRepo.getAll() }

        // Build update list from library + history
        val updates = remember(libraryEntries, historyEntries) {
            if (libraryEntries.isEmpty()) {
                emptyList()
            } else {
                // For each library entry, find the most recent history entry
                libraryEntries.mapNotNull { libEntry ->
                    val lastWatched = historyEntries
                        .filter { it.animeId == libEntry.animeId }
                        .maxByOrNull { it.seenAt }
                    if (lastWatched != null) {
                        UpdateItemData(
                            animeId = libEntry.animeId,
                            animeTitle = libEntry.title,
                            episodeId = lastWatched.episodeId,
                            episodeName = lastWatched.episodeName,
                            episodeNumber = lastWatched.episodeNumber,
                            seenAt = lastWatched.seenAt,
                            sourceId = libEntry.sourceId,
                        )
                    } else {
                        UpdateItemData(
                            animeId = libEntry.animeId,
                            animeTitle = libEntry.title,
                            episodeId = libEntry.animeId,
                            episodeName = "Added to library",
                            episodeNumber = 1.0,
                            seenAt = libEntry.addedAt,
                            sourceId = libEntry.sourceId,
                        )
                    }
                }.sortedByDescending { it.seenAt }
            }
        }

        UpdatesContent(
            updates = updates,
            libraryCount = libraryEntries.size,
            onUpdateClick = { update ->
                val libraryEntry = libraryEntries.find { it.animeId == update.animeId }
                if (libraryEntry != null && libraryEntry.sourceId != 0L) {
                    navigator.push(
                        AnimeDetailScreen(
                            animeId = update.animeId,
                            sourceId = libraryEntry.sourceId,
                            animeUrl = libraryEntry.url,
                            animeTitle = update.animeTitle,
                            extensionManager = extensionManager,
                        )
                    )
                } else {
                    toastHost.show(
                        text = "Cannot open update — source information missing",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        source = null,
                        location = "UpdatesTab.onUpdateClick",
                    )
                }
            },
        )
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u,
            title = "Updates",
            icon = rememberVectorPainter(Icons.Outlined.Refresh),
        )
}

data class UpdateItemData(
    val animeId: Long,
    val animeTitle: String,
    val episodeId: Long,
    val episodeName: String,
    val episodeNumber: Double,
    val seenAt: Long,
    val sourceId: Long = 0L,
    val isNew: Boolean = false,
)

@Composable
private fun UpdatesContent(
    updates: List<UpdateItemData>,
    libraryCount: Int = 0,
    onUpdateClick: (UpdateItemData) -> Unit = {},
) {
    if (updates.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (libraryCount > 0) "Checking for updates..." else "No recent updates",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (libraryCount > 0) "Add anime and watch episodes to see updates" else "Add anime to your library to track updates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "Recent Updates",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            items(
                items = updates,
                key = { it.episodeId },
            ) { update ->
                UpdatesItem(update = update, onClick = { onUpdateClick(update) })
            }
        }
    }
}

@Composable
private fun UpdatesItem(
    update: UpdateItemData,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = update.animeTitle.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = update.animeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = update.episodeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (update.isNew) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
