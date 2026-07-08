package android.content

actual class SharedPreferences {
    actual fun getString(key: String, defValue: String?): String? = defValue
    actual fun getInt(key: String, defValue: Int): Int = defValue
    actual fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    actual fun getLong(key: String, defValue: Long): Long = defValue
    actual fun edit(): Editor = Editor()

    actual class Editor {
        actual fun putString(key: String, value: String?): Editor { return this }
        actual fun putInt(key: String, value: Int): Editor { return this }
        actual fun putBoolean(key: String, value: Boolean): Editor { return this }
        actual fun remove(key: String): Editor { return this }
        actual fun clear(): Editor { return this }
        actual fun apply() {}
        actual fun commit(): Boolean = true
    }
}
