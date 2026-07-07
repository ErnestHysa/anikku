package android.app

/**
 * Stub for android.app.ActivityManager on macOS desktop.
 */
open class ActivityManager {
    open val memoryClass: Int get() = 512 // MB

    open fun getMemoryInfo(outInfo: MemoryInfo) {
        outInfo.totalMem = 16L * 1024 * 1024 * 1024 // 16 GB
        outInfo.availMem = 8L * 1024 * 1024 * 1024 // 8 GB
    }

    companion object {
        class MemoryInfo {
            var totalMem: Long = 0
            var availMem: Long = 0
            var lowMemory: Boolean = false
            var threshold: Long = 0
        }
    }
}
