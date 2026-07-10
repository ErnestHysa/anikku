package androidx.preference

import android.content.Context

class MultiSelectListPreference : Preference {
    constructor(context: Context) : super(context)

    var entries: Array<String> = emptyArray()
    var entryValues: Array<String> = emptyArray()

    fun setDefaultValue(value: Any?) {}
    fun setOnPreferenceChangeListener(listener: (Preference, Any?) -> Boolean) {}
}
