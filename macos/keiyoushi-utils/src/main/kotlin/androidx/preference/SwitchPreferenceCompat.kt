package androidx.preference

import android.content.Context

open class SwitchPreferenceCompat : Preference {
    constructor(context: Context) : super(context)

    fun setDefaultValue(value: Any?) {}
    fun setOnPreferenceChangeListener(listener: (Preference, Any?) -> Boolean) {}
}
