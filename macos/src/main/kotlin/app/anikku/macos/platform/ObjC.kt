package app.anikku.macos.platform

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * Shared JNA bridge for the Objective-C runtime (libobjc).
 *
 * Uses [NativeLibrary.getProcess] to look up symbols from the process's
 * loaded libraries. This avoids the "symbol not found" error that occurs
 * when declaring multiple JNA interface methods all mapping to `objc_msgSend`.
 *
 * Used by [MacOSFullScreen] and [MacOSDockManager] for calling macOS native
 * APIs via the Objective-C message dispatch system.
 */
object ObjC {

    /** Loaded instance of libobjc from the process address space. */
    private val lib: NativeLibrary by lazy {
        try {
            NativeLibrary.getInstance("objc")
        } catch (_: UnsatisfiedLinkError) {
            // Fall back to process-level symbol lookup (covers most JVM setups)
            NativeLibrary.getProcess()
        }
    }

    /** Cached `objc_msgSend` function handle. */
    private val msgSend: Function by lazy {
        lib.getFunction("objc_msgSend")
    }

    // -----------------------------------------------------------------------
    // Symbol lookup
    // -----------------------------------------------------------------------

    /** objc_getClass(const char *name) -> Class */
    fun objc_getClass(className: String): Pointer {
        return lib.getFunction("objc_getClass")
            .invoke(Pointer::class.java, arrayOf(className)) as Pointer
    }

    /** sel_registerName(const char *name) -> SEL */
    fun sel_registerName(name: String): Pointer {
        return lib.getFunction("sel_registerName")
            .invoke(Pointer::class.java, arrayOf(name)) as Pointer
    }

    // -----------------------------------------------------------------------
    // objc_msgSend — same native symbol, different arity/return-type overloads
    // -----------------------------------------------------------------------

    /** objc_msgSend(id, SEL) -> id */
    fun objc_msgSend(receiver: Pointer, selector: Pointer): Pointer {
        return msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector)) as Pointer
    }

    /** objc_msgSend(id, SEL, id) -> id */
    fun objc_msgSend(receiver: Pointer, selector: Pointer, arg: Pointer): Pointer {
        return msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector, arg)) as Pointer
    }

    /** objc_msgSend(id, SEL) -> void */
    fun objc_msgSend_void(receiver: Pointer, selector: Pointer) {
        msgSend.invoke(arrayOf(receiver, selector))
    }

    /** objc_msgSend(id, SEL, id) -> void */
    fun objc_msgSend_void(receiver: Pointer, selector: Pointer, arg: Pointer) {
        msgSend.invoke(arrayOf(receiver, selector, arg))
    }

    /** objc_msgSend(id, SEL, int64) -> void */
    fun objc_msgSend_void(receiver: Pointer, selector: Pointer, arg: Long) {
        msgSend.invoke(arrayOf(receiver, selector, arg))
    }

    /** objc_msgSend(id, SEL, id, id, id) -> void */
    fun objc_msgSend_void(receiver: Pointer, selector: Pointer, arg1: Pointer, arg2: Pointer, arg3: Pointer) {
        msgSend.invoke(arrayOf(receiver, selector, arg1, arg2, arg3))
    }

    /** objc_msgSend(id, SEL, const char *) -> id */
    fun objc_msgSend_str(receiver: Pointer, selector: Pointer, str: String): Pointer {
        // JNA auto-converts String → const char* when passed as Object
        return msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector, str)) as Pointer
    }

    /** objc_msgSend(id, SEL) -> long — for selectors returning NSInteger */
    fun objc_msgSend_long(receiver: Pointer, selector: Pointer): Long {
        return msgSend.invoke(Long::class.java, arrayOf(receiver, selector)) as Long
    }

    /** objc_msgSend(id, SEL, int64) -> long — for selectors taking an NSInteger arg and returning one */
    fun objc_msgSend_long(receiver: Pointer, selector: Pointer, arg: Long): Long {
        return msgSend.invoke(Long::class.java, arrayOf(receiver, selector, arg)) as Long
    }
}
