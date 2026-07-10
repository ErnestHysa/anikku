package androidx.preference

import android.content.Context

open class ListPreference : Preference {
    constructor(context: Context) : super(context)

    var entries: Array<String> = emptyArray()
    var entryValues: Array<String> = emptyArray()

    var value: String? = null

    fun setDefaultValue(value: Any?) {}
    fun setOnPreferenceChangeListener(listener: (Preference, Any?) -> Boolean) {}
    fun findIndexOfValue(value: String): Int = 0
}
