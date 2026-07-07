package android.os

import java.io.FileDescriptor

/**
 * Stub for android.os.ParcelFileDescriptor on macOS desktop.
 */
open class ParcelFileDescriptor(fd: FileDescriptor)

object FileUtils {
    fun closeQuietly(fd: FileDescriptor?) {}
    fun closeQuietly(pfd: ParcelFileDescriptor?) {}
}
