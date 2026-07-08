package android.content

import java.io.File

actual open class Context {
    actual val cacheDir: File = File(System.getProperty("java.io.tmpdir"), "cache")

    actual fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferences()

    actual companion object {
        actual const val MODE_PRIVATE: Int = 0
    }
}
