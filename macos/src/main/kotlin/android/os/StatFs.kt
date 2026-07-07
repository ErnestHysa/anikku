package android.os

/**
 * Stub for android.os.StatFs on macOS desktop.
 */
class StatFs(path: String) {
    fun getBlockCountLong(): Long = 1_000_000L
    fun getBlockSizeLong(): Long = 4096L
    fun getAvailableBlocksLong(): Long = 500_000L
    fun getFreeBlocksLong(): Long = 500_000L
    fun getTotalBytes(): Long = getBlockCountLong() * getBlockSizeLong()
    fun getAvailableBytes(): Long = getAvailableBlocksLong() * getBlockSizeLong()
    fun getFreeBytes(): Long = getFreeBlocksLong() * getBlockSizeLong()
}
