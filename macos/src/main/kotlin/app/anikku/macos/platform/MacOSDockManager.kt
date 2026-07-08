package app.anikku.macos.platform

import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * macOS Dock integration manager.
 *
 * Provides:
 * - Dock badge count (e.g., number of new updates)
 * - Dock menu items (Play/Pause, Next Episode) — Phase 9.6
 * - Dock icon bounce on new notifications
 *
 * Uses JNA to call the macOS Foundation framework's NSDockTile API
 * and the ApplicationKit's NSApplication APIs via [ObjC].
 *
 * ## Usage
 *
 * ```kotlin
 * MacOSDockManager.setBadgeCount(5) // Shows "5" on the dock icon
 * MacOSDockManager.setBadgeCount(0) // Clears badge
 * MacOSDockManager.requestUserAttention() // Bounce dock icon
 *
 * // Phase 9.6: Wire dock menu callbacks
 * MacOSDockManager.setPlayPauseCallback { playerViewModel.togglePause() }
 * MacOSDockManager.setNextEpisodeCallback { playerViewModel.nextEpisode() }
 * MacOSDockManager.createDockMenu()
 * ```
 */
object MacOSDockManager {

    private var isAvailable: Boolean = false

    /** Callback invoked when the dock's Play/Pause menu item is clicked. */
    var onPlayPause: () -> Unit = {}

    /** Callback invoked when the dock's Next Episode menu item is clicked. */
    var onNextEpisode: () -> Unit = {}

    /** Whether the Dock bridge is available. */
    val isDockAvailable: Boolean get() = isAvailable

    init {
        try {
            val osName = System.getProperty("os.name").lowercase()
            if (osName.contains("mac")) {
                // Verify JNA can load libobjc before marking as available
                ObjC.objc_getClass("NSObject")
                isAvailable = true
                logger.debug { "Dock manager initialized successfully" }
            } else {
                logger.warn { "Dock manager not available on: $osName" }
            }
        } catch (e: UnsatisfiedLinkError) {
            logger.warn(e) { "JNA libobjc not available, Dock integration disabled" }
            isAvailable = false
        } catch (e: Exception) {
            logger.warn(e) { "Failed to initialize Dock manager" }
            isAvailable = false
        }
    }

    /**
     * Set the badge count on the app's dock icon.
     * @param count The number to display (0 or negative clears the badge).
     */
    fun setBadgeCount(count: Int) {
        if (!isAvailable) return
        try {
            val nsApp = ObjC.objc_getClass("NSApplication")
            val sharedAppSel = ObjC.sel_registerName("sharedApplication")
            val sharedApp = ObjC.objc_msgSend(nsApp, sharedAppSel)
            val dockTileSel = ObjC.sel_registerName("dockTile")
            val dockTile = ObjC.objc_msgSend(sharedApp, dockTileSel)
            val setBadgeSel = ObjC.sel_registerName("setBadgeLabel:")

            if (count > 0) {
                val label = createNSString(count.toString())
                ObjC.objc_msgSend_void(dockTile, setBadgeSel, label)
            } else {
                ObjC.objc_msgSend_void(dockTile, setBadgeSel, Pointer.NULL)
            }
            logger.debug { "Dock badge set to: $count" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to set Dock badge count" }
        }
    }

    /** Clear the dock badge. */
    fun clearBadge() = setBadgeCount(0)

    /**
     * Bounce the dock icon to request user attention.
     * @param critical If true, continues bouncing until app is brought to front.
     */
    fun requestUserAttention(critical: Boolean = false) {
        if (!isAvailable) return
        try {
            val nsApp = ObjC.objc_getClass("NSApplication")
            val sharedAppSel = ObjC.sel_registerName("sharedApplication")
            val sharedApp = ObjC.objc_msgSend(nsApp, sharedAppSel)
            val requestSel = ObjC.sel_registerName("requestUserAttention:")
            val type = if (critical) 1L else 0L
            ObjC.objc_msgSend_void(sharedApp, requestSel, type)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to request user attention" }
        }
    }

    /**
     * Register the Play/Pause dock menu callback (Phase 9.6).
     */
    fun setPlayPauseCallback(callback: () -> Unit) {
        onPlayPause = callback
        logger.debug { "Dock Play/Pause callback registered" }
    }

    /**
     * Register the Next Episode dock menu callback (Phase 9.6).
     */
    fun setNextEpisodeCallback(callback: () -> Unit) {
        onNextEpisode = callback
        logger.debug { "Dock Next Episode callback registered" }
    }

    /**
     * Create and set the dock menu with Play/Pause and Next Episode items.
     *
     * Uses JNA/ObjC to call:
     * - `[[NSMenu alloc] initWithTitle:]`
     * - `[menu addItemWithTitle:action:keyEquivalent:]`
     * - `[NSApp setDockMenu:menu]`
     *
     * **Note:** Action selectors are currently set to empty (no-op) because
     * JNA ObjC callback forwarding requires additional infrastructure.
     * The callbacks stored in [onPlayPause] and [onNextEpisode] are available
     * for future integration when ObjC block bridging is implemented.
     *
     * The dock menu items display correctly in the macOS Dock context menu
     * even without wired actions — users can see available controls.
     */
    fun createDockMenu() {
        if (!isAvailable) return
        try {
            val nsApp = ObjC.objc_getClass("NSApplication")
            val sharedAppSel = ObjC.sel_registerName("sharedApplication")
            val sharedApp = ObjC.objc_msgSend(nsApp, sharedAppSel)

            // Create NSMenu
            val nsMenuCls = ObjC.objc_getClass("NSMenu")
            val allocSel = ObjC.sel_registerName("alloc")
            val allocedMenu = ObjC.objc_msgSend(nsMenuCls, allocSel)
            val initTitleSel = ObjC.sel_registerName("initWithTitle:")
            val menuTitle = createNSString("DockMenu")
            val dockMenu = ObjC.objc_msgSend(allocedMenu, initTitleSel, menuTitle)

            // Add "Play / Pause" menu item
            addMenuItem(dockMenu, "Play / Pause")

            // Add separator
            val separatorItemCls = ObjC.objc_getClass("NSMenuItem")
            val sepSel = ObjC.sel_registerName("separatorItem")
            val separator = ObjC.objc_msgSend(separatorItemCls, sepSel)
            val addItemSel = ObjC.sel_registerName("addItem:")
            ObjC.objc_msgSend_void(dockMenu, addItemSel, separator)

            // Add "Next Episode" menu item
            addMenuItem(dockMenu, "Next Episode")

            // Set as dock menu
            val setDockMenuSel = ObjC.sel_registerName("setDockMenu:")
            ObjC.objc_msgSend_void(sharedApp, setDockMenuSel, dockMenu)

            logger.info { "Dock menu created with Play/Pause and Next Episode items" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to create Dock menu" }
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Create an NSString from a Kotlin String via alloc/initWithUTF8String:.
     */
    private fun createNSString(str: String): Pointer {
        val nsStrCls = ObjC.objc_getClass("NSString")
        val allocSel = ObjC.sel_registerName("alloc")
        val initSel = ObjC.sel_registerName("initWithUTF8String:")
        val alloced = ObjC.objc_msgSend(nsStrCls, allocSel)
        return ObjC.objc_msgSend_str(alloced, initSel, str)
    }

    /**
     * Add an NSMenuItem to an NSMenu with the given title.
     * Uses `[menu addItemWithTitle:action:keyEquivalent:]`.
     */
    private fun addMenuItem(menu: Pointer, title: String) {
        val addItemSel = ObjC.sel_registerName("addItemWithTitle:action:keyEquivalent:")
        val nsTitle = createNSString(title)
        val emptyAction = ObjC.sel_registerName("")
        val emptyKey = createNSString("")
        ObjC.objc_msgSend_void(menu, addItemSel, nsTitle, emptyAction, emptyKey)
    }
}
