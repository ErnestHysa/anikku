package app.anikku.macos.ui.screens.anime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.MacOSShareUtil
import app.anikku.macos.platform.preference.LocalBookmarkStore
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.screens.models.EpisodeModel
import app.anikku.macos.ui.screens.models.MockData
import app.anikku.macos.ui.screens.player.PlayerScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/**
 * Anime detail screen — Phase 5.7.
 *
 * Displays anime information (cover, title, description, status)
 * and a list of episodes.
 *
 * Uses local [AnimeModel] and [EpisodeModel] data until domain layer is connected.
 * Navigated to from Library, Updates, History, or Browse screens.
 *
 * @param animeId The ID of the anime to display.
 */
data class AnimeDetailScreen(
    val animeId: Long,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val bookmarkStore = LocalBookmarkStore.current
        val focusRequester = remember { FocusRequester() }

        // Look up anime from MockData by ID
        var anime by remember { mutableStateOf(MockData.sampleAnime.find { it.id == animeId }) }
        var episodes by remember {
            val bookmarkedIds = bookmarkStore.getBookmarkedIds()
            val baseEpisodes = MockData.sampleEpisodes.filter { it.animeId == animeId }
            mutableStateOf(
                baseEpisodes.map { ep ->
                    if (ep.id in bookmarkedIds) ep.copy(bookmark = true) else ep
                }
            )
        }

        // Request focus on mount so keyboard shortcuts work immediately
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.S &&
                        event.isMetaPressed
                    ) {
                        // Share action — matches tooltip hint "Share  (⌘S)"
                        val url = anime?.url
                        if (url != null) {
                            val shared = MacOSShareUtil.shareUrl(
                                title = "Anikku",
                                text = url,
                                description = "${anime?.title} — URL copied to clipboard",
                            )
                            if (shared) {
                                toastHost.show("URL ready to share", ToastDuration.SHORT)
                            } else {
                                toastHost.show("Could not share", ToastDuration.SHORT)
                            }
                        } else {
                            toastHost.show("No URL available", ToastDuration.SHORT)
                        }
                        true
                    } else {
                        false
                    }
                },
        ) {
            AnimeDetailContent(
                anime = anime,
                episodes = episodes,
                isFavorite = anime?.favorite ?: false,
                onToggleFavorite = {
                    val updated = anime?.copy(favorite = !anime!!.favorite)
                    if (updated != null) {
                        anime = updated
                        if (updated.favorite) {
                            toastHost.show("Added to favorites", ToastDuration.SHORT)
                        } else {
                            toastHost.show("Removed from favorites", ToastDuration.SHORT)
                        }
                    }
                },
                onToggleBookmark = { episodeId ->
                    val newState = bookmarkStore.toggleBookmark(episodeId)
                    episodes = episodes.map { ep ->
                        if (ep.id == episodeId) {
                            ep.copy(bookmark = newState)
                        } else {
                            ep
                        }
                    }
                    toastHost.show(if (newState) "Episode bookmarked" else "Bookmark removed", ToastDuration.SHORT)
                },
                onMarkAllSeen = {
                    val unseenCount = episodes.count { !it.seen }
                    if (unseenCount == 0) {
                        toastHost.show("All episodes already seen", ToastDuration.SHORT)
                    } else {
                        episodes = episodes.map { it.copy(seen = true) }
                        toastHost.show("Marked $unseenCount episodes as seen", ToastDuration.SHORT)
                    }
                },
                onShare = {
                    val url = anime?.url
                    if (url != null) {
                        val shared = MacOSShareUtil.shareUrl(
                            title = "Anikku",
                            text = url,
                            description = "${anime?.title} — URL copied to clipboard",
                        )
                        if (shared) {
                            toastHost.show("URL ready to share", ToastDuration.SHORT)
                        } else {
                            toastHost.show("Could not share", ToastDuration.SHORT)
                        }
                    } else {
                        toastHost.show("No URL available", ToastDuration.SHORT)
                    }
                },
                onCopyUrl = {
                    val url = anime?.url
                    if (url != null) {
                        try {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(url), null)
                            toastHost.show("URL copied to clipboard", ToastDuration.SHORT)
                        } catch (_: Exception) {
                            toastHost.show("Could not copy URL", ToastDuration.SHORT)
                        }
                    } else {
                        toastHost.show("No URL available", ToastDuration.SHORT)
                    }
                },
                onOpenInBrowser = {
                    val url = anime?.url
                    if (url != null) {
                        try {
                            Desktop.getDesktop().browse(URI(url))
                        } catch (_: Exception) {
                            toastHost.show("Could not open browser", ToastDuration.SHORT)
                        }
                    } else {
                        toastHost.show("No URL available", ToastDuration.SHORT)
                    }
                },
                onBack = { navigator.pop() },
                onPlayEpisode = { episode ->
                    navigator.push(PlayerScreen(animeId = animeId, episodeId = episode.id))
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AnimeDetailContent(
    anime: AnimeModel?,
    episodes: List<EpisodeModel>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleBookmark: (Long) -> Unit,
    onShare: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onBack: () -> Unit,
    onMarkAllSeen: () -> Unit = {},
    onPlayEpisode: (EpisodeModel) -> Unit,
) {
    if (anime == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = anime.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    // Favorite toggle
                    TooltipArea(
                        tooltip = {
                            TooltipContent("Add to favorites  (⌘D)")
                        },
                        delayMillis = 500,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(8.dp, 8.dp),
                        ),
                    ) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (isFavorite)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Share
                    TooltipArea(
                        tooltip = {
                            TooltipContent("Share  (⌘S)")
                        },
                        delayMillis = 500,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(8.dp, 8.dp),
                        ),
                    ) {
                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share",
                            )
                        }
                    }
                    // Copy URL
                    TooltipArea(
                        tooltip = {
                            TooltipContent("Copy URL  (⌘⇧C)")
                        },
                        delayMillis = 500,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(8.dp, 8.dp),
                        ),
                    ) {
                        IconButton(onClick = onCopyUrl) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy URL",
                            )
                        }
                    }
                    // Open in browser
                    TooltipArea(
                        tooltip = {
                            TooltipContent("Open in browser  (⌘⇧O)")
                        },
                        delayMillis = 500,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(8.dp, 8.dp),
                        ),
                    ) {
                        IconButton(onClick = onOpenInBrowser) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInBrowser,
                                contentDescription = "Open in browser",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Cover + Info header
            item(key = "header") {
                AnimeInfoHeader(
                    anime = anime,
                    episodes = episodes,
                    onPlayEpisode = onPlayEpisode,
                    onShare = onShare,
                    onCopyUrl = onCopyUrl,
                    onOpenInBrowser = onOpenInBrowser,
                )
            }

            // Description
            item(key = "description") {
                if (!anime.description.isNullOrBlank()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = anime.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Episodes header
            item(key = "episodes_header") {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(12.dp))
                        TextButton(
                            onClick = onMarkAllSeen,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Mark all seen",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Text(
                        text = "${episodes.size} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Episode list
            items(
                items = episodes,
                key = { it.id },
            ) { episode ->
                EpisodeItem(
                    episode = episode,
                    onClick = { onPlayEpisode(episode) },
                    onToggleBookmark = { onToggleBookmark(episode.id) },
                )
            }

            if (episodes.isEmpty()) {
                item(key = "no_episodes") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No episodes available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeInfoHeader(
    anime: AnimeModel,
    episodes: List<EpisodeModel>,
    onPlayEpisode: (EpisodeModel) -> Unit,
    onShare: () -> Unit = {},
    onCopyUrl: () -> Unit = {},
    onOpenInBrowser: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Cover image loaded via Coil 3
        AnimeCoverImage(
            thumbnailUrl = anime.thumbnailUrl,
            contentDescription = anime.title,
            title = anime.title,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp)),
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(4.dp))

            // Status chip
            val statusText = when (anime.status) {
                1 -> "Ongoing"
                2 -> "Completed"
                3 -> "Licensed"
                4 -> "Publishing Finished"
                5 -> "Cancelled"
                6 -> "On Hiatus"
                else -> "Unknown"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (!anime.author.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "by ${anime.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Genre tags
            if (!anime.genre.isNullOrEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    anime.genre.take(3).forEach { genre ->
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Text(
                                text = genre,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val firstUnseen = episodes.firstOrNull { !it.seen }
                    if (firstUnseen != null) {
                        onPlayEpisode(firstUnseen)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Continue Watching")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                enabled = anime.url != null,
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Share")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onCopyUrl,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                enabled = anime.url != null,
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Copy URL")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenInBrowser,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                enabled = anime.url != null,
            ) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Open in Browser")
            }
        }
    }
}

/**
 * Small styled tooltip content composable used by [TooltipArea] wrappers.
 */
@Composable
private fun TooltipContent(text: String) {
    Surface(
        modifier = Modifier.shadow(4.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

@Composable
private fun EpisodeItem(
    episode: EpisodeModel,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (episode.seen)
                MaterialTheme.colorScheme.surfaceContainerLow
            else
                MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Seen indicator
            Icon(
                imageVector = if (episode.seen)
                    Icons.Outlined.CheckCircle
                else
                    Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (episode.seen) "Seen" else "Unseen",
                tint = if (episode.seen)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Episode ${String.format("%.0f", episode.episodeNumber)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!episode.seen) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Bookmark toggle
            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (episode.bookmark) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (episode.bookmark) "Remove bookmark" else "Bookmark episode",
                    tint = if (episode.bookmark)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }

            if (!episode.seen) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
