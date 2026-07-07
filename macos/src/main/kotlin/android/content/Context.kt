package android.content

import java.io.File

/**
 * Minimal stub for android.content.Context on macOS desktop.
 * Provides the bare minimum API surface for shared code compilation.
 * Full Context-dependent functionality is replaced by MacOSStorageProvider, etc.
 */
open class Context {

    open fun getExternalFilesDir(type: String?): File? {
        return null
    }

    open fun getFilesDir(): File {
        return File(System.getProperty("user.dir") ?: ".")
    }

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return SharedPreferences()
    }

    open val applicationContext: Context
        get() = this
}
