package android.os

/**
 * Stub for android.os.SystemClock on macOS desktop.
 */
object SystemClock {
    @JvmStatic
    fun elapsedRealtime(): Long = System.currentTimeMillis()

    @JvmStatic
    fun uptimeMillis(): Long = System.currentTimeMillis()
}
