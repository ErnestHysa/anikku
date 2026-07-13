package app.anikku.macos.ui.screens.browse

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Global search screen — searches ALL installed anime sources in parallel.
 *
 * Replicates the Android app's cross-extension search experience:
 * 1. Type a query → searches all CatalogueSource extensions simultaneously
 * 2. Results are grouped by source with source name as a header
 * 3. Each source shows its own loading spinner while searching
 * 4. Click any result → navigates to AnimeDetailScreen for that source/anime
 *
 * UX improvements over individual source browsing:
 * - No need to click into each source individually
 * - See results from all sources at once
 * - Real-time parallel search with per-source progress indicators
 */
data class GlobalSearchScreen(
    val extensionManager: MacOSExtensionManager? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    data class SourceSearchResult(
        val sourceId: Long,
        val sourceName: String,
        val sourceLang: String,
        val anime: List<AnimeModel>,
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current

        var searchQuery by remember { mutableStateOf("") }
        var hasSearched by remember { mutableStateOf(false) }

        // Per-source results — use mutableStateListOf so Compose tracks changes per source
        val sourceResults = remember { mutableStateListOf<SourceSearchResult>() }

        // Get all CatalogueSource extensions
        val installedExtensions by remember(extensionManager) {
            extensionManager?.installedExtensionsFlow
                ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        }.collectAsState()

        val catalogueSources = remember(installedExtensions) {
            installedExtensions.flatMap { ext ->
                ext.sources.filterIsInstance<CatalogueSource>()
            }.distinctBy { it.id }
        }

        // Debounced search — fires 500ms after user stops typing
        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) {
                sourceResults.clear()
                hasSearched = false
                return@LaunchedEffect
            }

            // Debounce
            delay(500)

            val query = searchQuery
            if (query.isBlank()) return@LaunchedEffect

            hasSearched = true

            // Initialize loading state for all sources
            sourceResults.clear()
            catalogueSources.forEach { source ->
                sourceResults.add(
                    SourceSearchResult(
                        sourceId = source.id,
                        sourceName = source.name,
                        sourceLang = source.lang,
                        anime = emptyList(),
                        isLoading = true,
                    )
                )
            }

            UIActionLogger.logClick("GlobalSearch", query, "search_all_sources", "sources=${catalogueSources.size}")

            // Search all sources in parallel with timeout + thread safety
            val results = withContext(Dispatchers.IO) {
                val deferredResults = catalogueSources.map { source ->
                    async {
                        val index = sourceResults.indexOfFirst { it.sourceId == source.id }
                        try {
                            val page = withTimeout(20_000L) {
                                source.getSearchAnime(
                                    page = 1,
                                    query = query,
                                    filters = AnimeFilterList(),
                                )
                            }
                            val animeModels = page.animes.map { it.toAnimeModel(source.id) }
                            Triple(index, animeModels, null)
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            Triple(index, emptyList<AnimeModel>(), "Search timed out after 20s")
                        } catch (e: Exception) {
                            Triple(index, emptyList<AnimeModel>(), e.message?.take(80) ?: "Unknown error")
                        }
                    }
                }
                deferredResults.awaitAll()
            }

            // Update state on main thread
            results.forEach { (index, animeModels, error) ->
                if (index >= 0 && index < sourceResults.size) {
                    sourceResults[index] = sourceResults[index].copy(
                        anime = animeModels,
                        isLoading = false,
                        error = error,
                    )
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Global Search", maxLines = 1) },
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
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Search bar — prominent, full width
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    placeholder = { Text("Search all sources for anime...") },
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
                                onClick = {
                                    searchQuery = ""
                                    sourceResults.clear()
                                    hasSearched = false
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )

                // Show "searching..." text while results load
                val loadingSources = sourceResults.count { it.isLoading }
                if (loadingSources > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Searching $loadingSources source${if (loadingSources != 1) "s" else ""}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                // No results state (after search completed)
                if (hasSearched && sourceResults.all { !it.isLoading && it.anime.isEmpty() }) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No results for \"$searchQuery\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Try a different search term or check your spelling",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    return@Column
                }

                // Results list — grouped by source
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    sourceResults
                        .filter { !it.isLoading && it.anime.isNotEmpty() }
                        .forEach { result ->
                            // Source header
                            item(key = "header-${result.sourceId}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            result.sourceName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "· ${result.sourceLang.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "${result.anime.size} result${if (result.anime.size != 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // Anime cards for this source
                            items(
                                items = result.anime,
                                key = { "${result.sourceId}-${it.id}" },
                            ) { anime ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .clickable {
                                            UIActionLogger.logClick(
                                                "GlobalSearch",
                                                "${result.sourceName} - ${anime.title}",
                                                "select_anime",
                                                "id=${anime.id}"
                                            )
                                            navigator.push(
                                                AnimeDetailScreen(
                                                    animeId = anime.id,
                                                    sourceId = result.sourceId,
                                                    animeUrl = anime.url,
                                                    animeTitle = anime.title,
                                                    extensionManager = extensionManager,
                                                )
                                            )
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Small cover thumbnail
                                        AnimeCoverImage(
                                            thumbnailUrl = anime.thumbnailUrl,
                                            contentDescription = anime.title,
                                            title = anime.title,
                                            modifier = Modifier.size(56.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                anime.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (!anime.genre.isNullOrEmpty()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    anime.genre.take(3).joinToString(", "),
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
                        }

                    // Show sources that had errors
                    val erroredSources = sourceResults.filter { it.error != null }
                    if (erroredSources.isNotEmpty()) {
                        item(key = "errors-header") {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${erroredSources.size} source${if (erroredSources.size != 1) "s" else ""} failed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        items(erroredSources, key = { "err-${it.sourceId}" }) { result ->
                            Text(
                                text = "${result.sourceName}: ${result.error}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}
