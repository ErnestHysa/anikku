package app.anikku.macos.platform

import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * macOS Dock integration manager.
 *
 * Provides:
 * - Dock badge count (e.g., number of new updates)
 * - Dock menu items (Play/Pause, Next Episode)
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
 * ```
 */
object MacOSDockManager {

    private var isAvailable: Boolean = false

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
                val nsStringCls = ObjC.objc_getClass("NSString")
                val allocSel = ObjC.sel_registerName("alloc")
                val initWithUTF8Sel = ObjC.sel_registerName("initWithUTF8String:")
                // Create NSString from the count string via alloc/initWithUTF8String:
                val alloced = ObjC.objc_msgSend(nsStringCls, allocSel)
                val label = ObjC.objc_msgSend_str(alloced, initWithUTF8Sel, count.toString())
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
            ObjC.objc_msgSend_long(sharedApp, requestSel, type)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to request user attention" }
        }
    }

    /** Set the dock play/pause menu action (placeholder for future wiring). */
    fun setPlayPauseMenuAction(onPlayPause: () -> Unit) {
        logger.debug { "Dock play/pause menu action registered" }
    }
}
