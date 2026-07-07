package android.content

/**
 * Minimal stub for android.content.SharedPreferences on macOS desktop.
 */
open class SharedPreferences {

    open fun getString(key: String, defaultValue: String?): String? = defaultValue
    open fun getLong(key: String, defaultValue: Long): Long = defaultValue
    open fun getInt(key: String, defaultValue: Int): Int = defaultValue
    open fun getFloat(key: String, defaultValue: Float): Float = defaultValue
    open fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    open fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? = defaultValue

    open fun contains(key: String): Boolean = false

    open fun getAll(): Map<String, *> = emptyMap<String, Any>()

    open fun edit(): Editor = Editor()

    open class Editor {
        open fun putString(key: String, value: String?): Editor = this
        open fun putLong(key: String, value: Long): Editor = this
        open fun putInt(key: String, value: Int): Editor = this
        open fun putFloat(key: String, value: Float): Editor = this
        open fun putBoolean(key: String, value: Boolean): Editor = this
        open fun putStringSet(key: String, value: Set<String>?): Editor = this
        open fun remove(key: String): Editor = this
        open fun apply() {}
        open fun commit(): Boolean = true
        open fun clear(): Editor = this
    }
}
