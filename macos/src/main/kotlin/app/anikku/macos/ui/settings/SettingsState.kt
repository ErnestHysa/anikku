package app.anikku.macos.ui.settings

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.ui.theme.AnikkuTheme

/**
 * Mutable settings state accessible throughout the Compose tree via CompositionLocal.
 *
 * Holds user-configurable preferences that affect the app's appearance.
 * When a [MacOSPreferenceStore] is provided, theme and AMOLED preferences
 * are persisted to a JSON file so they survive app restarts.
 *
 * Usage:
 * - **Read**: `LocalSettingsState.current.theme` — get current theme
 * - **Write**: `settingsState.theme = AnikkuTheme.Theme.SAPPHIRE` — change theme (auto-saves)
 * - **AMOLED**: `settingsState.isAmoledOLED = true` — toggle AMOLED black (auto-saves)
 */
class SettingsState(
    private val preferenceStore: MacOSPreferenceStore? = null,
) {

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_AMOLED_OLED = "amoled_oled"
    }

    private val themePref = preferenceStore?.getString(KEY_THEME, AnikkuTheme.Theme.DEFAULT.name)
    private val amoledPref = preferenceStore?.getBoolean(KEY_AMOLED_OLED, false)

    /** Backing state for the current theme. Loaded from preferences when store is available. */
    private val _theme = mutableStateOf(loadTheme())

    /** Currently selected color scheme theme. Setting this auto-persists to disk when store is available. */
    var theme: AnikkuTheme.Theme
        get() = _theme.value
        set(value) {
            _theme.value = value
            themePref?.set(value.name)
        }

    /** Backing state for AMOLED mode. Loaded from preferences when store is available. */
    private val _isAmoledOLED = mutableStateOf(amoledPref?.get() ?: false)

    /** Whether to use AMOLED pure black backgrounds in dark mode. Setting this auto-persists to disk when store is available. */
    var isAmoledOLED: Boolean
        get() = _isAmoledOLED.value
        set(value) {
            _isAmoledOLED.value = value
            amoledPref?.set(value)
        }

    /** Load theme from preferences if store is available, falling back to DEFAULT. */
    private fun loadTheme(): AnikkuTheme.Theme {
        val name = themePref?.get() ?: return AnikkuTheme.Theme.DEFAULT
        return try {
            AnikkuTheme.Theme.valueOf(name)
        } catch (_: Exception) {
            AnikkuTheme.Theme.DEFAULT
        }
    }
}

/**
 * CompositionLocal providing the mutable [SettingsState] to the Compose tree.
 * Must be provided via CompositionLocalProvider in AnikkuApp.kt.
 */
val LocalSettingsState = compositionLocalOf { SettingsState() }
