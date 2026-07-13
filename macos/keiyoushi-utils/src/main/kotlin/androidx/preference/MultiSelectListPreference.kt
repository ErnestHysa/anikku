package androidx.preference

import android.content.Context

class MultiSelectListPreference : Preference {
    constructor(context: Context) : super(context)

    var entries: Array<String> = emptyArray()
    var entryValues: Array<String> = emptyArray()

    override fun setDefaultValue(value: Any?) {}
    override fun setOnPreferenceChangeListener(listener: (Preference, Any?) -> Boolean) {}
}
