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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.screens.models.toAnimeModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Source browse screen — shows anime catalog from a selected source.
 *
 * Fetches popular anime from the source's [CatalogueSource.getPopularAnime] API on load.
 * Supports searching via [CatalogueSource.getSearchAnime] when the user types a query.
 * Shows error state when no compatible extension source is installed.
 */
data class SourceBrowseScreen(
    val sourceId: Long,
    val sourceName: String,
    val extensionManager: MacOSExtensionManager? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    // ── Compose state stored at class level ─────────────────────────
    //
    // Voyager disposes the composition tree when this screen is pushed
    // to the backstack (e.g. when AnimeDetailScreen is pushed on top).
    // Standard `remember` blocks are lost on disposal.
    //
    // By storing mutable state as class-level properties, we survive
    // composition disposal because Voyager retains the Screen instance
    // in its backstack. When the user presses Back, this same instance
    // is recomposed with all state intact.
    private val _isLoading = mutableStateOf(true)
    private val _isSearching = mutableStateOf(false)
    private val _errorMessage = mutableStateOf<String?>(null)
    private val _animeList = mutableStateOf(emptyList<AnimeModel>())
    private val _searchQuery = mutableStateOf("")
    private val _isShowingSearchResults = mutableStateOf(false)
    private val _hasSearched = mutableStateOf(false)

    var isLoading: Boolean by _isLoading
    var isSearching: Boolean by _isSearching
    var errorMessage: String? by _errorMessage
    var animeList: List<AnimeModel> by _animeList
    var searchQuery: String by _searchQuery
    var isShowingSearchResults: Boolean by _isShowingSearchResults
    var hasSearched: Boolean by _hasSearched
    // ── End of class-level state ────────────────────────────────────

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current

        // Fetch popular anime on first composition.
        // Skip re-fetch on back-navigation when state survived (class-level state
        // preserved by Voyager's backstack). Otherwise, re-fetching popular anime
        // would overwrite any search results the user had before navigating away.
        LaunchedEffect(sourceId) {
            if (animeList.isNotEmpty()) return@LaunchedEffect

            isLoading = true
            errorMessage = null

            val source = extensionManager?.getSource(sourceId)
            if (source is CatalogueSource) {
                UIActionLogger.logExtension(sourceName, "fetchPopular", "sourceId=$sourceId")
                // Run the suspend call on IO dispatcher — extensions may do blocking I/O
                try {
                    val page = withContext(Dispatchers.IO) {
                        source.getPopularAnime(page = 1)
                    }
                    animeList = page.animes.map { it.toAnimeModel(sourceId) }
                    UIActionLogger.logExtension(sourceName, "popularResults", "count=${animeList.size}")
                } catch (e: NoClassDefFoundError) {
                    errorMessage = "Missing JVM dependency: ${e.message}. " +
                        "This source needs a JVM-compatible build. Try building from source with: ./gradlew buildKeiyoushiExtension"
                    toastHost.show("Missing dependency for $sourceName", ToastDuration.LONG)
                } catch (e: Exception) {
                    errorMessage = "${e::class.simpleName}: ${e.message}"
                    toastHost.show("$sourceName: ${e::class.simpleName} — ${e.message?.take(80)}", ToastDuration.LONG)
                }
            } else if (source != null) {
                errorMessage = "Source does not support catalog browsing (missing CatalogueSource interface)"
            } else {
                errorMessage = "Source not found — install an extension via the Extensions tab"
            }

            isLoading = false
        }

        // Debounced search — fires 400ms after the user stops typing.
        // On back-navigation, LaunchedEffect re-launches because remember
        // doesn't survive composition disposal. The class-level state guard
        // prevents redundant re-search when cached results exist.
        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) {
                // Clear search results — reload popular if we had searched
                if (isShowingSearchResults) {
                    isShowingSearchResults = false
                    hasSearched = false
                    val source = extensionManager?.getSource(sourceId)
                    if (source is CatalogueSource && errorMessage == null) {
                        try {
                            isLoading = true
                            val page = source.getPopularAnime(page = 1)
                            animeList = page.animes.map { it.toAnimeModel(sourceId) }
                        } catch (_: Exception) {
                            toastHost.show("Failed to reload popular anime", ToastDuration.SHORT)
                        }
                        isLoading = false
                    }
                }
                return@LaunchedEffect
            }

            // Skip re-search if state survived back-navigation with results
            if (hasSearched && animeList.isNotEmpty()) {
                return@LaunchedEffect
            }

            // Debounce 400ms
            delay(400)

            val source = extensionManager?.getSource(sourceId)
            if (source is CatalogueSource && errorMessage == null) {
                isSearching = true
                isShowingSearchResults = true
                hasSearched = true
                errorMessage = null
                UIActionLogger.logExtension(sourceName, "search", "query=$searchQuery")
                try {
                    val page = source.getSearchAnime(page = 1, query = searchQuery, filters = AnimeFilterList())
                    animeList = page.animes.map { it.toAnimeModel(sourceId) }
                    UIActionLogger.logExtension(sourceName, "searchResults", "count=${animeList.size}, query=$searchQuery")
                } catch (e: Exception) {
                    errorMessage = "Search error: ${e.message}"
                    toastHost.show("Search failed: ${e.message?.take(80)}", ToastDuration.LONG)
                }
                isSearching = false
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        onValueChange = {
                            // Reset searched flag on any user edit so the guard
                            // doesn't block re-search when the query changes
                            hasSearched = false
                            searchQuery = it
                        },
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
