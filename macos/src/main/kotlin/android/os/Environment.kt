package android.os

import java.io.File

/**
 * Stub for android.os.Environment on macOS desktop.
 */
object Environment {
    const val MEDIA_MOUNTED = "mounted"

    @JvmStatic
    fun getExternalStorageState(): String = MEDIA_MOUNTED

    @JvmStatic
    fun getExternalStorageDirectory(): File =
        File(System.getProperty("user.home") ?: ".")

    @JvmStatic
    fun getExternalStoragePublicDirectory(type: String): File =
        File(getExternalStorageDirectory(), type)

    @JvmStatic
    fun isExternalStorageEmulated(): Boolean = false

    @JvmStatic
    fun isExternalStorageRemovable(): Boolean = false

    const val DIRECTORY_DOWNLOADS = "Downloads"
    const val DIRECTORY_PICTURES = "Pictures"
    const val DIRECTORY_DOCUMENTS = "Documents"
    const val DIRECTORY_MUSIC = "Music"
    const val DIRECTORY_MOVIES = "Movies"
}
