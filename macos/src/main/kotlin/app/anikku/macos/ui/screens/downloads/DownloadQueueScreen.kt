package app.anikku.macos.ui.screens.downloads

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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey

/**
 * Download queue screen — Phase 5.10 UI shell.
 *
 * Shows ongoing and completed downloads with progress bars.
 * Supports pause/resume and cancellation of individual downloads.
 *
 * TODO Phase 7: Connect to real DownloadManager when domain layer is wired.
 */
class DownloadQueueScreen : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        // Mock download items for display
        val downloads = remember {
            listOf(
                DownloadItem(
                    id = 1L,
                    animeTitle = "Attack on Titan",
                    episodeName = "Episode 3 - A Dim Light Amid Despair",
                    progress = 0.45f,
                    status = DownloadStatus.Downloading,
                    totalBytes = 250_000_000L,
                    downloadedBytes = 112_500_000L,
                ),
                DownloadItem(
                    id = 2L,
                    animeTitle = "Jujutsu Kaisen",
                    episodeName = "Episode 22 - The Origin of Obedience (Part 2)",
                    progress = 0.78f,
                    status = DownloadStatus.Downloading,
                    totalBytes = 180_000_000L,
                    downloadedBytes = 140_400_000L,
                ),
                DownloadItem(
                    id = 3L,
                    animeTitle = "One Piece",
                    episodeName = "Episode 1092 - A Night to Remember",
                    progress = 1.0f,
                    status = DownloadStatus.Completed,
                ),
                DownloadItem(
                    id = 4L,
                    animeTitle = "Spy x Family",
                    episodeName = "Episode 38 - Enjoy the Resort to the Fullest",
                    progress = 0.0f,
                    status = DownloadStatus.Paused,
                ),
                DownloadItem(
                    id = 5L,
                    animeTitle = "Demon Slayer",
                    episodeName = "Episode 5 - Final Selection",
                    progress = 1.0f,
                    status = DownloadStatus.Completed,
                ),
            )
        }

        val toastHost = LocalToastHost.current

        DownloadQueueContent(
            downloads = downloads,
            onPauseResume = { id ->
                val item = downloads.find { it.id == id }
                if (item != null) {
                    val action = if (item.status == DownloadStatus.Paused) "Resumed" else "Paused"
                    toastHost.show("$action: ${item.animeTitle}", ToastDuration.SHORT)
                }
            },
            onCancel = { id ->
                val item = downloads.find { it.id == id }
                if (item != null) {
                    toastHost.show("Cancelled: ${item.animeTitle}", ToastDuration.SHORT)
                }
            },
        )
    }
}

private data class DownloadItem(
    val id: Long,
    val animeTitle: String,
    val episodeName: String,
    val progress: Float,
    val status: DownloadStatus,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
)

private enum class DownloadStatus { Downloading, Paused, Completed, Error }

@Composable
private fun DownloadQueueContent(
    downloads: List<DownloadItem>,
    onPauseResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
) {
    val activeDownloads = downloads.count { it.status == DownloadStatus.Downloading }
    val completedDownloads = downloads.count { it.status == DownloadStatus.Completed }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "$activeDownloads active · $completedDownloads completed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (downloads.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No downloads",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Downloaded episodes will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        } else {
            items(
                items = downloads,
                key = { it.id },
            ) { item ->
                DownloadItemCard(
                    item = item,
                    onPauseResume = { onPauseResume(item.id) },
                    onCancel = { onCancel(item.id) },
                )
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItem,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.animeTitle.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.animeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.episodeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Status icon
                when (item.status) {
                    DownloadStatus.Downloading -> {
                        IconButton(onClick = onPauseResume, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.PauseCircle,
                                contentDescription = "Pause",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    DownloadStatus.Paused -> {
                        IconButton(onClick = onPauseResume, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = "Resume",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    DownloadStatus.Completed -> {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DownloadStatus.Error -> {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (item.status != DownloadStatus.Completed) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Progress bar for downloading items
            if (item.status == DownloadStatus.Downloading || item.status == DownloadStatus.Paused) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (item.status == DownloadStatus.Paused)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatBytes(item.downloadedBytes) + " / " + formatBytes(item.totalBytes) +
                        " (${(item.progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
