package app.anikku.macos.ui.settings

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.anikku.macos.ui.theme.AnikkuTheme

/**
 * Mutable settings state accessible throughout the Compose tree via CompositionLocal.
 *
 * Holds user-configurable preferences that affect the app's appearance.
 * Future phases will add library, player, and other settings.
 *
 * Usage:
 * - **Read**: `SettingsState.current.theme` — get current theme
 * - **Write**: `settingsState.theme = AnikkuTheme.Theme.SAPPHIRE` — change theme
 * - **AMOLED**: `settingsState.isAmoledOLED = true` — toggle AMOLED black
 */
class SettingsState {

    /** Currently selected color scheme theme. */
    var theme: AnikkuTheme.Theme by mutableStateOf(AnikkuTheme.Theme.DEFAULT)

    /** Whether to use AMOLED pure black backgrounds in dark mode. */
    var isAmoledOLED: Boolean by mutableStateOf(false)
}

/**
 * CompositionLocal providing the mutable [SettingsState] to the Compose tree.
 * Must be provided via CompositionLocalProvider in AnikkuApp.kt.
 */
val LocalSettingsState = compositionLocalOf { SettingsState() }
