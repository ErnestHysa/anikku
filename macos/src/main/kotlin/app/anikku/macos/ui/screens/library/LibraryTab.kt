package app.anikku.macos.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeGrid
import app.anikku.macos.ui.components.AnimeList
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.screens.models.MockData
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions

/**
 * Library screen tab — Phase 5.
 *
 * Shows the user's anime library in either grid or list mode with mock data.
 * When the domain/data modules are wired into the macOS build, this will
 * connect to [GetLibraryAnime] for real library data.
 *
 * TODO: Connect to GetLibraryAnime interactor when domain modules are available.
 */
object LibraryTab : AnikkuScreen(), Tab {

    enum class DisplayMode { Grid, List }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var displayMode by remember { mutableStateOf(DisplayMode.Grid) }

        // Using MockData until domain layer is connected
        val libraryAnime = remember { MockData.sampleAnime }

        LibraryContent(
            libraryAnime = libraryAnime,
            displayMode = displayMode,
            onToggleDisplayMode = {
                displayMode = if (displayMode == DisplayMode.Grid) DisplayMode.List else DisplayMode.Grid
            },
            onAnimeClick = { anime ->
                navigator.push(AnimeDetailScreen(anime.id))
            },
        )
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = "Library",
            icon = rememberVectorPainter(Icons.Outlined.Book),
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    libraryAnime: List<AnimeModel>,
    displayMode: LibraryTab.DisplayMode,
    onToggleDisplayMode: () -> Unit,
    onAnimeClick: (AnimeModel) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onToggleDisplayMode) {
                        Icon(
                            imageVector = if (displayMode == LibraryTab.DisplayMode.Grid)
                                Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                            contentDescription = if (displayMode == LibraryTab.DisplayMode.Grid)
                                "Switch to list" else "Switch to grid",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (libraryAnime.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Book,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Your library is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Browse sources and add anime to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            AnimatedContent(
                targetState = displayMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "library_display",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) { mode ->
                when (mode) {
                    LibraryTab.DisplayMode.Grid -> {
                        AnimeGrid(
                            items = libraryAnime,
                            onClick = onAnimeClick,
                            columns = 3,
                            getSubtitle = { anime ->
                                when (anime.status) {
                                    1 -> "Ongoing"
                                    2 -> "Completed"
                                    3 -> "Licensed"
                                    4 -> "Finished"
                                    5 -> "Cancelled"
                                    6 -> "On Hiatus"
                                    else -> "Unknown"
                                }
                            },
                        )
                    }
                    LibraryTab.DisplayMode.List -> {
                        AnimeList(
                            items = libraryAnime,
                            onClick = onAnimeClick,
                            getSubtitle = { anime ->
                                when (anime.status) {
                                    1 -> "Ongoing"
                                    2 -> "Completed"
                                    else -> null
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
