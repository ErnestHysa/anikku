package app.anikku.macos.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

    enum class SortMode { Title, Status, LastUpdated }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var displayMode by remember { mutableStateOf(DisplayMode.Grid) }
        var searchQuery by remember { mutableStateOf("") }
        var sortMode by remember { mutableStateOf(SortMode.Title) }
        var showSortMenu by remember { mutableStateOf(false) }

        val allAnime = remember { MockData.sampleAnime }
        val filteredAnime = remember(allAnime, searchQuery, sortMode) {
            val filtered = if (searchQuery.isBlank()) allAnime
            else allAnime.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.author?.contains(searchQuery, ignoreCase = true) == true ||
                    it.genre?.any { g -> g.contains(searchQuery, ignoreCase = true) } == true
            }
            when (sortMode) {
                SortMode.Title -> filtered.sortedBy { it.title }
                SortMode.Status -> filtered.sortedBy { it.status }
                SortMode.LastUpdated -> filtered.reversed()
            }
        }

        LibraryContent(
            libraryAnime = filteredAnime,
            displayMode = displayMode,
            searchQuery = searchQuery,
            sortMode = sortMode,
            showSortMenu = showSortMenu,
            onSearchQueryChange = { searchQuery = it },
            onToggleDisplayMode = {
                displayMode = if (displayMode == DisplayMode.Grid) DisplayMode.List else DisplayMode.Grid
            },
            onSortModeChange = { sortMode = it },
            onToggleSortMenu = { showSortMenu = !showSortMenu },
            onDismissSortMenu = { showSortMenu = false },
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
    searchQuery: String,
    sortMode: LibraryTab.SortMode,
    showSortMenu: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleDisplayMode: () -> Unit,
    onSortModeChange: (LibraryTab.SortMode) -> Unit,
    onToggleSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onAnimeClick: (AnimeModel) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    Box {
                        IconButton(onClick = onToggleSortMenu) {
                            Icon(
                                imageVector = Icons.Outlined.Sort,
                                contentDescription = "Sort",
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = onDismissSortMenu,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Title") },
                                onClick = { onSortModeChange(LibraryTab.SortMode.Title); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibraryTab.SortMode.Title)
                                        Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Status") },
                                onClick = { onSortModeChange(LibraryTab.SortMode.Status); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibraryTab.SortMode.Status)
                                        Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Last Updated") },
                                onClick = { onSortModeChange(LibraryTab.SortMode.LastUpdated); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibraryTab.SortMode.LastUpdated)
                                        Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                        }
                    }

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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (libraryAnime.isNotEmpty() || searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search library...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            if (libraryAnime.isEmpty() && searchQuery.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
            } else if (libraryAnime.isEmpty() && searchQuery.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No results for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AnimatedContent(
                    targetState = displayMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "library_display",
                    modifier = Modifier.fillMaxSize(),
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
}
