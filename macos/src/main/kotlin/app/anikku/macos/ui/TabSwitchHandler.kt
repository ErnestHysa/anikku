package app.anikku.macos.ui

/**
 * Bridges AWT menu bar View shortcuts (⌘1-4) to the Voyager TabNavigator.
 *
 * Since the menu bar is set up via java.awt in AnikkuApp.kt (outside the
 * Compose tree), and the Voyager TabNavigator lives inside MainWindow.kt
 * (inside Compose), this object provides the callback channel between them.
 *
 * Usage:
 * - **Menu side**: `TabSwitchHandler.switchTo(0)` → switches to Library tab
 * - **Compose side**: In MainWindow, set `TabSwitchHandler.onSwitchTab = { ... }`
 */
object TabSwitchHandler {

    /** Maps tab index (0-4) to the Voyager tab switching action. */
    var onSwitchTab: ((Int) -> Unit)? = null

    /** Convenience: switch to a tab by its index (0=Library, 1=Updates, 2=History, 3=Browse, 4=More). */
    fun switchTo(index: Int) {
        onSwitchTab?.invoke(index)
    }
}
