package app.anikku.macos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.anikku.macos.ui.MacOSMenuBarFactory
import app.anikku.macos.platform.MacOSDockManager
import app.anikku.macos.ui.GlobalKeyboardShortcuts
import app.anikku.macos.ui.components.AboutDialog
import app.anikku.macos.ui.screens.browse.BrowseTab
import app.anikku.macos.ui.screens.onboarding.OnboardingScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import app.anikku.macos.ui.MainWindow
import app.anikku.macos.ui.TabSwitchHandler
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.preference.BookmarkStore
import app.anikku.macos.platform.preference.LocalBookmarkStore
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.auth.TrackerManager
import app.anikku.macos.platform.auth.TrackerOAuthManager
import app.anikku.macos.platform.auth.TrackerTokenStore
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
 * - Material 3 theme system (18+ color schemes)
 * - Voyager tab navigation with desktop sidebar rail
 * - Coil 3 image loading with OkHttp network layer
 * - Onboarding flow on first launch (Phase 5.12)
 * - Dock menu with Play/Pause, Next Episode (Phase 9.6)
 */
fun main() = application {
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
        val settingsState = remember { SettingsState(app.preferenceStore) }
        val bookmarkStore = remember { BookmarkStore(app.preferenceStore) }
        val libraryRepository = remember { app.libraryRepository }
        val historyRepository = remember { app.historyRepository }
        val toastHostState = remember { ToastHostState() }

        var showAboutDialog by remember { mutableStateOf(false) }

        // Phase 5.12: Check if onboarding has been completed
        val onboardingCompletePref = remember {
            app.preferenceStore.getBoolean("onboarding_completed", false)
        }
        var showOnboarding by remember {
            mutableStateOf(!onboardingCompletePref.get())
        }

        // Set up the macOS native menu bar via java.awt
        val onQuit = {
            app.onShutdown()
            exitApplication()
        }
        val onSettings = { TabSwitchHandler.switchTo(4) }
        val onOpenBackup = { /* Future: open file picker for .tachibk backup */ }
        val onAbout = { showAboutDialog = true }
        (window as? Frame)?.let { frame ->
            MacOSMenuBarFactory.attach(frame, onQuit, onSettings, onOpenBackup, onAbout)
        }

        // Phase 9.2: Initialize global keyboard shortcuts
        GlobalKeyboardShortcuts.initialize(
            onToggleSidebar = { /* Future: toggle sidebar visibility */ },
            onOpenSearch = { /* Future: focus search in current screen */ },
            onOpenSettings = { TabSwitchHandler.switchTo(4) },
            onNewSource = { TabSwitchHandler.switchTo(3) },
        )

        // Phase 5.6: Wire extension manager to BrowseTab
        BrowseTab.setExtensionManager(app.extensionManager)

        // Phase 9.6: Initialize Dock integration
        MacOSDockManager.setBadgeCount(0) // Clear badge on launch
        MacOSDockManager.createDockMenu() // Create dock menu with Play/Pause and Next Episode

        val trackerManager = remember {
            TrackerManager(
                oauthManager = app.networkHelper.client.let { TrackerOAuthManager(it) },
                tokenStore = app.preferenceStore.let { TrackerTokenStore(it) },
                httpClient = app.networkHelper.client,
            )
        }

        CompositionLocalProvider(
            LocalSettingsState provides settingsState,
            LocalBookmarkStore provides bookmarkStore,
            LocalLibraryRepository provides libraryRepository,
            LocalHistoryRepository provides historyRepository,
            LocalToastHost provides toastHostState,
            LocalTrackerManager provides trackerManager,
        ) {
            AnikkuTheme(
                theme = settingsState.theme,
                isAmoledOLED = settingsState.isAmoledOLED,
                isDarkOverride = settingsState.themeMode,
            ) {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    if (showOnboarding) {
                        OnboardingScreen(
                            onComplete = {
                                onboardingCompletePref.set(true)
                                showOnboarding = false
                            },
                        ).Content()
                    } else {
                        MainWindow()
                    }
                    ToastHost(state = toastHostState)
                }

                if (showAboutDialog) {
                    AboutDialog(onCloseRequest = { showAboutDialog = false })
                }
            }
        }
    }
}
