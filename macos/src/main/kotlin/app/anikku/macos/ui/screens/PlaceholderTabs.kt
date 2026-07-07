package app.anikku.macos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikku.macos.ui.AnikkuScreen
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions

object LibraryScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        PlaceholderTabContent(
            title = "Library",
            description = "Your anime library will appear here.\nBrowse, filter, and manage your collection.",
            icon = Icons.Outlined.LibraryBooks,
        )
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = "Library",
            icon = rememberVectorPainter(Icons.Outlined.LibraryBooks),
        )
}

object UpdatesScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        PlaceholderTabContent(
            title = "Updates",
            description = "New episode updates will appear here.\nStay up to date with your favorite anime.",
            icon = Icons.Outlined.Refresh,
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

object HistoryScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        PlaceholderTabContent(
            title = "History",
            description = "Your watch history will appear here.\nPick up where you left off.",
            icon = Icons.Outlined.History,
        )
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 2u,
            title = "History",
            icon = rememberVectorPainter(Icons.Outlined.History),
        )
}

object BrowseScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        PlaceholderTabContent(
            title = "Browse",
            description = "Browse anime sources and extensions.\nDiscover new content from your favorite sources.",
            icon = Icons.Outlined.Explore,
        )
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 3u,
            title = "Browse",
            icon = rememberVectorPainter(Icons.Outlined.Explore),
        )
}

object MoreScreen : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        PlaceholderTabContent(
            title = "More",
            description = "Settings, backups, statistics, and more.\nConfigure Anikku to your liking.",
            icon = Icons.Outlined.MoreVert,
        )
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 4u,
            title = "More",
            icon = rememberVectorPainter(Icons.Outlined.MoreVert),
        )
}

/**
 * Placeholder content for tabs that haven't been fully ported yet.
 * Each tab will be replaced with real content in Phase 5.
 */
@Composable
private fun PlaceholderTabContent(
    title: String,
    description: String,
    icon: ImageVector,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
