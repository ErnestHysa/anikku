package app.anikku.macos.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Frame

/**
 * macOS-native fullscreen toggle via JNA/Objective-C bridge.
 *
 * Calls `[NSWindow toggleFullScreen:]` on the NSWindow underlying
 * an AWT Frame, producing proper macOS fullscreen Space behavior
 * (like the green traffic light button), unlike AWT's game-style
 * exclusive fullscreen which hides the menu bar and Dock.
 *
 * Requires JVM args:
 *   --add-exports java.desktop/sun.lwawt.macosx=ALL-UNNAMED
 *   --add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED
 */
object MacOSFullScreen {

    /** True if we successfully toggled fullscreen at least once (implies JNA bridge works). */
    var isJnaAvailable: Boolean = false
        private set

    /**
     * Toggles macOS-native fullscreen Space for the given AWT Frame.
     *
     * Uses JNA to call the Objective-C runtime directly, sending
     * `toggleFullScreen:` to the Frame's underlying NSWindow.
     *
     * This must be called on the AWT Event Dispatch Thread (EDT),
     * which is the case when invoked from an AWT MenuItem action listener.
     *
     * @param frame The AWT Frame whose NSWindow should toggle fullscreen
     * @return true if the toggle was invoked successfully
     */
    fun toggleFullScreen(frame: Frame): Boolean {
        return try {
            val nsWindowPtr = getNSWindowPointer(frame)
            if (nsWindowPtr == 0L) {
                isJnaAvailable = false
                return false
            }

            val selector = ObjC.INSTANCE.sel_registerName("toggleFullScreen:")
            ObjC.INSTANCE.objc_msgSend(
                Pointer.createConstant(nsWindowPtr),
                selector,
                Pointer.NULL,
            )
            isJnaAvailable = true
            true
        } catch (e: Exception) {
            // JNA bridge not available (e.g., wrong JDK, missing --add-exports)
            isJnaAvailable = false
            false
        }
    }

    /**
     * Retrieves the native NSWindow pointer from an AWT Frame.
     *
     * Uses the internal `sun.lwawt.macosx.CPlatformWindow` API.
     * Returns 0 if the peer is not yet initialized or the internal
     * API is inaccessible (missing --add-exports).
     */
    private fun getNSWindowPointer(frame: Frame): Long {
        return try {
            val peer = frame.javaClass.getMethod("getPeer").invoke(frame)
            val platformWindow = peer.javaClass
                .getMethod("getPlatformWindow")
                .invoke(peer)
            platformWindow.javaClass
                .getMethod("getNSWindowPtr")
                .invoke(platformWindow) as Long
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * JNA mapping for the Objective-C runtime (libobjc.dylib).
 */
private interface ObjC : Library {
    companion object {
        val INSTANCE: ObjC = Native.load("objc", ObjC::class.java)
    }

    /**
     * objc_msgSend(id receiver, SEL selector, ...)
     * Sends a message with one pointer argument (the sender, typically nil).
     */
    fun objc_msgSend(receiver: Pointer, selector: Pointer, arg: Pointer)

    /**
     * sel_registerName(const char *name) -> SEL
     * Returns a SEL pointer for the given selector name.
     */
    fun sel_registerName(name: String): Pointer
}
