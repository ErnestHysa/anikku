package app.anikku.macos

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.anikku.macos.ui.MainWindow
import app.anikku.macos.ui.theme.AnikkuTheme
import app.anikku.macos.ui.theme.AnikkuTheme.Theme
import androidx.compose.ui.unit.dp

/**
 * Anikku macOS — Entry Point.
 *
 * Launches the Compose Desktop application with:
 * - macOS application initialization (logging, Koin DI, storage, database)
 * - Material 3 theme system (18+ color schemes)
 * - Voyager tab navigation with desktop sidebar rail
 *
 * Phase 4: UI Framework & Navigation
 */
fun main() = application {
    // Initialize the macOS application (logging, Koin DI, storage, database, networking)
    val app = AnikkuApplication()

    Window(
        onCloseRequest = {
            app.onShutdown()
            exitApplication()
        },
        title = "Anikku",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        // Apply the Anikku theme system
        AnikkuTheme(
            theme = Theme.DEFAULT,
            isAmoledOLED = false,
        ) {
            // Main window with Voyager TabNavigator + sidebar rail
            MainWindow()
        }
    }
}
