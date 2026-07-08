package app.anikku.macos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.anikku.macos.ui.MacOSMenuBarFactory
import app.anikku.macos.platform.MacOSDockManager
import app.anikku.macos.ui.GlobalKeyboardShortcuts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import app.anikku.macos.ui.MainWindow
import app.anikku.macos.ui.TabSwitchHandler
import app.anikku.macos.platform.preference.BookmarkStore
import app.anikku.macos.platform.preference.LocalBookmarkStore
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastHost
import app.anikku.macos.ui.components.ToastHostState
import app.anikku.macos.ui.settings.LocalSettingsState
import app.anikku.macos.ui.settings.SettingsState
import app.anikku.macos.ui.theme.AnikkuTheme
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.awt.Frame

/**
 * Anikku macOS — Entry Point.
 *
 * Launches the Compose Desktop application with:
 * - macOS application initialization (logging, Koin DI, storage, database)
 * - Native menu bar (File, Edit, View, Window, Help) per AD-04 (Phase 9.1)
 *   via java.awt native menu bar (Compose Desktop MenuBar API unavailable in 1.11.x)
 * - Material 3 theme system (18+ color schemes)
 * - Voyager tab navigation with desktop sidebar rail
 * - Coil 3 image loading with OkHttp network layer
 */
fun main() = application {
    // Configure the global Coil ImageLoader for Compose Desktop
    // Uses OkHttp for network requests (shared HTTP client with the app)
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .build()
    }
    val app = AnikkuApplication()

    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 800.dp),
    )

    Window(
        onCloseRequest = {
            app.onShutdown()
            exitApplication()
        },
        title = "Anikku",
        state = windowState,
    ) {
        // Shared settings state — wired to both the theme system and the Settings screen
        val settingsState = remember { SettingsState(app.preferenceStore) }
        // Shared bookmark store — persists bookmarked episode IDs across restarts
        val bookmarkStore = remember { BookmarkStore(app.preferenceStore) }
        val toastHostState = remember { ToastHostState() }

        // Set up the macOS native menu bar via java.awt
        // (Compose Desktop MenuBar/Menu/Item composable API unavailable in 1.11.x)
        val onQuit = {
            app.onShutdown()
            exitApplication()
        }
        val onSettings = { TabSwitchHandler.switchTo(4) }
        val onOpenBackup = { /* Future: open file picker for .tachibk backup */ }
        (window as? Frame)?.let { frame ->
            MacOSMenuBarFactory.attach(frame, onQuit, onSettings, onOpenBackup)
        }

        // Phase 9.2: Initialize global keyboard shortcuts
        GlobalKeyboardShortcuts.initialize(
            onToggleSidebar = { /* Future: toggle sidebar visibility */ },
            onOpenSearch = { /* Future: focus search in current screen */ },
            onOpenSettings = { TabSwitchHandler.switchTo(4) },
            onNewSource = { TabSwitchHandler.switchTo(3) },
        )

        // Phase 9.6: Initialize Dock integration
        MacOSDockManager.setBadgeCount(0) // Clear badge on launch

        CompositionLocalProvider(
            LocalSettingsState provides settingsState,
            LocalBookmarkStore provides bookmarkStore,
            LocalToastHost provides toastHostState,
        ) {
            AnikkuTheme(
                theme = settingsState.theme,
                isAmoledOLED = settingsState.isAmoledOLED,
                isDarkOverride = settingsState.themeMode,
            ) {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    MainWindow()
                    ToastHost(state = toastHostState)
                }
            }
        }
    }
}
