package app.anikku.macos.ui.screens.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.screens.models.MockData
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

/**
 * Source browse screen — shows anime catalog from a selected source.
 *
 * Currently displays mock data. When source integrations are wired,
 * this will fetch the anime list from the source API.
 */
data class SourceBrowseScreen(
    val sourceId: Long,
    val sourceName: String,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var searchQuery by remember { mutableStateOf("") }

        // Use MockData for now — replace with source API when wired
        val animeList = remember {
            MockData.sampleAnime.filter { anime ->
                searchQuery.isBlank() || anime.title.contains(searchQuery, ignoreCase = true)
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(animeList, key = { it.id }) { anime ->
                    SourceAnimeItem(
                        anime = anime,
                        onClick = {
                            navigator.push(AnimeDetailScreen(animeId = anime.id))
                        },
                    )
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
