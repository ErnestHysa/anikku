package app.anikku.macos.ui.screens.browse

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.screens.models.MockData
import app.anikku.macos.ui.screens.models.toAnimeModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.delay

/**
 * Source browse screen — shows anime catalog from a selected source.
 *
 * Fetches popular anime from the source's [CatalogueSource.getPopularAnime] API on load.
 * Supports searching via [CatalogueSource.getSearchAnime] when the user types a query.
 * Falls back to [MockData] when no compatible extension source is installed.
 */
data class SourceBrowseScreen(
    val sourceId: Long,
    val sourceName: String,
    val extensionManager: MacOSExtensionManager? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        var isLoading by remember { mutableStateOf(true) }
        var isSearching by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var animeList by remember { mutableStateOf(emptyList<AnimeModel>()) }
        var usingFallback by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var isShowingSearchResults by remember { mutableStateOf(false) }
        var hasSearched by remember { mutableStateOf(false) }

        // Fetch popular anime on first composition
        LaunchedEffect(sourceId) {
            isLoading = true
            errorMessage = null

            val source = extensionManager?.getSource(sourceId)
            if (source is CatalogueSource) {
                try {
                    val page = source.getPopularAnime(page = 1)
                    animeList = page.animeList.map { it.toAnimeModel(sourceId) }
                } catch (e: Exception) {
                    errorMessage = "Source API error: ${e.message}"
                    animeList = MockData.sampleAnime
                    usingFallback = true
                }
            } else if (source != null) {
                errorMessage = "Source does not support catalog browsing"
                animeList = MockData.sampleAnime
                usingFallback = true
            } else {
                errorMessage = "Source not found. Showing sample data."
                animeList = MockData.sampleAnime
                usingFallback = true
            }

            isLoading = false
        }

        // Debounced search — fires 400ms after the user stops typing
        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) {
                // Clear search results — reload popular if we had searched
                if (isShowingSearchResults) {
                    isShowingSearchResults = false
                    hasSearched = false
                    val source = extensionManager?.getSource(sourceId)
                    if (source is CatalogueSource && !usingFallback) {
                        try {
                            isLoading = true
                            val page = source.getPopularAnime(page = 1)
                            animeList = page.animeList.map { it.toAnimeModel(sourceId) }
                        } catch (_: Exception) { }
                        isLoading = false
                    }
                }
                return@LaunchedEffect
            }

            // Debounce 400ms
            delay(400)

            val source = extensionManager?.getSource(sourceId)
            if (source is CatalogueSource && !usingFallback) {
                isSearching = true
                isShowingSearchResults = true
                hasSearched = true
                errorMessage = null
                try {
                    val page = source.getSearchAnime(page = 1, query = searchQuery)
                    animeList = page.animeList.map { it.toAnimeModel(sourceId) }
                } catch (e: Exception) {
                    errorMessage = "Search error: ${e.message}"
                }
                isSearching = false
            } else if (usingFallback) {
                // Filter MockData locally
                isShowingSearchResults = true
                hasSearched = true
                animeList = MockData.sampleAnime.filter {
                    it.title.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (usingFallback) {
                                Text(
                                    "Demo mode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        placeholder = { Text("Search anime...") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = if (searchQuery.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        ),
                    )

                    // Loading state
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Loading anime...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        return@Box
                    }

                    // Search status
                    if (isSearching) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Searching...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        return@Box
                    }

                    if (hasSearched && animeList.isEmpty()) {
                        // No search results
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No results for \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Try a different search term",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                        return@Box
                    }

                    if (animeList.isEmpty() && !isShowingSearchResults) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No anime found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        return@Box
                    }

                    // Result count
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isShowingSearchResults)
                                "${animeList.size} result${if (animeList.size != 1) "s" else ""} for \"$searchQuery\""
                            else
                                "${animeList.size} title${if (animeList.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Anime grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(animeList, key = { it.id }) { anime ->
                            SourceAnimeItem(
                                anime = anime,
                                onClick = {
                                    navigator.push(
                                        AnimeDetailScreen(
                                            animeId = anime.id,
                                            sourceId = sourceId,
                                            animeUrl = anime.url,
                                            animeTitle = anime.title,
                                            extensionManager = extensionManager,
                                        )
                                    )
                                },
                            )
                        }
                    }
                }

                // Error banner
                errorMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceAnimeItem(
    anime: AnimeModel,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column {
            AnimeCoverImage(
                thumbnailUrl = anime.thumbnailUrl,
                contentDescription = anime.title,
                title = anime.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!anime.genre.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = anime.genre.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
