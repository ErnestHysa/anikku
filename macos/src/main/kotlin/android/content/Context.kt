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
 * preference store via the Injekt bridge.
 */
open class Context {

    private val noopEditor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun putStringSet(key: String, value: Set<String>?) = this
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
        override fun getStringSet(key: String, defValues: Set<String>?) = defValues
        override fun contains(key: String) = false
        override val all: Map<String, *> get() = emptyMap<String, Any>()
    }

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = noopPrefs

    val applicationContext: Context get() = this

    fun getBaseContext(): Context = this

    fun getFilesDir(): java.io.File {
        val dir = java.io.File(System.getProperty("user.home"), ".Anikku/files")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDir(name: String, mode: Int): java.io.File {
        val dir = java.io.File(getFilesDir(), name)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getExternalFilesDir(type: String?): java.io.File = getFilesDir()

    fun getString(resId: Int): String = ""

    fun getResources(): android.content.res.Resources = android.content.res.Resources()

    fun getPackageManager(): android.content.pm.PackageManager = android.content.pm.PackageManager()

    fun getContentResolver(): android.content.ContentResolver = android.content.ContentResolver()

    fun getSystemService(name: String): Any? = null

    fun checkSelfPermission(permission: String): Int =
        android.content.pm.PackageManager.PERMISSION_GRANTED

    fun startActivity(intent: Intent) {}

    fun sendBroadcast(intent: Intent) {}

    fun registerReceiver(
        receiver: android.content.BroadcastReceiver?,
        filter: IntentFilter?,
    ): Intent? = null

    fun unregisterReceiver(receiver: android.content.BroadcastReceiver?) {}

    fun getPackageName(): String = "app.anikku.macos"
}
