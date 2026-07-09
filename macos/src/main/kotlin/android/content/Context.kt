package android.content

/**
 * Stub for `android.content.Context` on macOS JVM.
 *
 * Extensions compiled for Android call `getSharedPreferences()` on the
 * injected `Application` instance (which extends `Context` in Android).
 * This stub provides the method so method dispatch works without
 * throwing `NoSuchMethodError`.
 *
 * Returns a no-op [SharedPreferences] implementation that ignores all
 * operations. Extension preferences are handled through the app's own
 * [tachiyomi.core.common.preference.PreferenceStore] via the Injekt bridge.
 */
open class Context {

    private val noopEditor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun remove(key: String) = this
        override fun clear() = this
        override fun apply() {}
        override fun commit(): Boolean = true
    }

    private val noopPrefs = object : SharedPreferences {
        override fun edit() = noopEditor
        override fun getString(key: String, defValue: String?) = defValue
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getBoolean(key: String, defValue: Boolean) = defValue
        override fun contains(key: String) = false
        override fun all() = emptyMap<String, Any>()
    }

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = noopPrefs

    fun getApplicationContext(): Context = this

    fun getBaseContext(): Context = this

    fun getFilesDir(): java.io.File? = null

    fun getDir(name: String, mode: Int): java.io.File? = null
}
