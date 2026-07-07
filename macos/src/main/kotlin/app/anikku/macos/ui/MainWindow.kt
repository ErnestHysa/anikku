package app.anikku.macos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.anikku.macos.ui.screens.BrowseScreen
import app.anikku.macos.ui.screens.HistoryScreen
import app.anikku.macos.ui.screens.LibraryScreen
import app.anikku.macos.ui.screens.MoreScreen
import app.anikku.macos.ui.screens.UpdatesScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut

/**
 * Main application window composable.
 *
 * Sets up the Voyager TabNavigator with 5 primary tabs:
 * Library, Updates, History, Browse, More
 *
 * Uses a Material 3 NavigationRail (side navigation) for desktop layout,
 * matching macOS conventions where horizontal space is abundant.
 *
 * Ported from the Android HomeScreen.kt and MainActivity.kt.
 */
/** Ordered tabs matching the View menu shortcuts (⌘1-4, ⌘5 for More). */
private val orderedTabs: List<Tab> = listOf(
    LibraryScreen,
    UpdatesScreen,
    HistoryScreen,
    BrowseScreen,
    MoreScreen,
)

@Composable
fun MainWindow() {
    TabNavigator(
        tab = LibraryScreen,
        key = "MainWindowTabs",
    ) { tabNavigator ->
        val navigator = LocalNavigator.currentOrThrow

        // Bridge ⌘1-4/⌘5 View menu shortcuts to Voyager tab switching
        DisposableEffect(tabNavigator) {
            TabSwitchHandler.onSwitchTab = { index ->
                orderedTabs.getOrNull(index)?.let { tab ->
                    tabNavigator.current = tab
                }
            }
            onDispose {
                TabSwitchHandler.onSwitchTab = null
            }
        }

        // Use a NavigationRail on desktop for side navigation
        CompositionLocalProvider(LocalNavigator provides navigator) {
            Surface(
                color = MaterialTheme.colorScheme.background,
            ) {
                Row {
                    // Side Navigation Rail
                    NavigationRailSidebar()

                    // Main content area with fade-through animation
                    AnimatedContent(
                        targetState = tabNavigator.current,
                        transitionSpec = {
                            materialFadeThroughIn(
                                initialScale = 1f,
                                durationMillis = 200,
                            ) togetherWith materialFadeThroughOut(durationMillis = 200)
                        },
                        label = "tabContent",
                    ) { tab ->
                        tabNavigator.saveableState(key = "currentTab_${tab.key}", tab) {
                            CurrentTab()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Desktop NavigationRail sidebar composable.
 *
 * Renders the 5 primary tabs as NavigationRailItems.
 * On desktop, this appears as a fixed left sidebar with icons and labels.
 */
@Composable
private fun NavigationRailSidebar() {
    val tabNavigator = LocalTabNavigator.current

    androidx.compose.material3.NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        orderedTabs.forEach { tab ->
            val selected = tabNavigator.current::class == tab::class
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        tabNavigator.current = tab
                    }
                },
                icon = {
                    tab.options.icon?.let { painter ->
                        Icon(
                            painter = painter,
                            contentDescription = tab.options.title,
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.options.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                alwaysShowLabel = true,
            )
        }
    }
}
