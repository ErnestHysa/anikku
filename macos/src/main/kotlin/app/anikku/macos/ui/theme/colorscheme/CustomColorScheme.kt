package app.anikku.macos.ui.theme.colorscheme

import androidx.compose.material3.ColorScheme

/**
 * macOS Custom color scheme.
 *
 * Generates a Material 3 color scheme from a user-selected seed color.
 * For Phase 4, falls back to TachiyomiColorScheme. Dynamic seed-based
 * color generation will be wired in Phase 5 when the settings UI is ported.
 */
class CustomColorScheme(
    seedColor: Int = 0xFF0058CA.toInt(),
) : BaseColorScheme() {

    private val fallback = TachiyomiColorScheme

    override val darkScheme: ColorScheme
        get() = fallback.darkScheme

    override val lightScheme: ColorScheme
        get() = fallback.lightScheme
}
