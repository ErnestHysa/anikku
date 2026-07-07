package app.anikku.macos

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import app.anikku.macos.ui.MacOSMenuBarFactory
import app.anikku.macos.ui.MainWindow
import app.anikku.macos.ui.TabSwitchHandler
import app.anikku.macos.ui.settings.LocalSettingsState
import app.anikku.macos.ui.settings.SettingsState
import app.anikku.macos.ui.theme.AnikkuTheme
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
 */
fun main() = application {
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

        // Set up the macOS native menu bar via java.awt
        // (Compose Desktop MenuBar/Menu/Item composable API unavailable in 1.11.x)
        val onQuit = { exitApplication() }
        val onSettings = { TabSwitchHandler.switchTo(4) }
        val onOpenBackup = { /* TODO: Phase 7 — Open file picker for .tachibk backup */ }
        (window as? Frame)?.let { frame ->
            MacOSMenuBarFactory.attach(frame, onQuit, onSettings, onOpenBackup)
        }

        CompositionLocalProvider(LocalSettingsState provides settingsState) {
            AnikkuTheme(
                theme = settingsState.theme,
                isAmoledOLED = settingsState.isAmoledOLED,
            ) {
                MainWindow()
            }
        }
    }
}
