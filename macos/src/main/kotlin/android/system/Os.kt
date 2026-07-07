package android.system

/**
 * Stubs for android.system on macOS desktop.
 */
object Os {
    fun lseek(fd: Int, offset: Long, whence: Int): Long = 0
    fun read(fd: Int, buffer: ByteArray, offset: Int, byteCount: Int): Int = 0
    fun close(fd: Int) {}
}

object OsConstants {
    const val SEEK_SET = 0
    const val SEEK_CUR = 1
    const val SEEK_END = 2
    const val O_RDONLY = 0
}
