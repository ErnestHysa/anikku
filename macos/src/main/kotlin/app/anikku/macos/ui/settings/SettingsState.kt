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
/**
 * Theme mode: follow system, force light, or force dark.
 * Addresses Phase 9.7: Dark Mode Detection.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class SettingsState(
    private val preferenceStore: MacOSPreferenceStore? = null,
) {

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_AMOLED_OLED = "amoled_oled"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    private val themePref = preferenceStore?.getString(KEY_THEME, AnikkuTheme.Theme.DEFAULT.name)
    private val amoledPref = preferenceStore?.getBoolean(KEY_AMOLED_OLED, false)
    private val themeModePref = preferenceStore?.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)

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

    /** Backing state for the theme mode (system/light/dark). Phase 9.7. */
    private val _themeMode = mutableStateOf(loadThemeMode())

    /**
     * Theme mode: SYSTEM (follow macOS), LIGHT (force light), or DARK (force dark).
     * Setting this auto-persists to disk when store is available.
     */
    var themeMode: ThemeMode
        get() = _themeMode.value
        set(value) {
            _themeMode.value = value
            themeModePref?.set(value.name)
        }

    /**
     * Resolve the effective dark mode from the theme mode and system preference.
     * This is called from @Composable context (AnikkuApp.kt passes it to AnikkuTheme).
     * @param isSystemDark Whether the macOS system is in dark mode.
     */
    fun resolveIsDark(isSystemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
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

    /** Load theme mode from preferences if store is available, falling back to SYSTEM. */
    private fun loadThemeMode(): ThemeMode {
        val name = themeModePref?.get() ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }
}

/**
 * CompositionLocal providing the mutable [SettingsState] to the Compose tree.
 * Must be provided via CompositionLocalProvider in AnikkuApp.kt.
 */
val LocalSettingsState = compositionLocalOf { SettingsState() }
