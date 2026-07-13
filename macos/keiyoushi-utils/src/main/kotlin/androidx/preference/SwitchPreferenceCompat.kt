package androidx.preference

import android.content.Context

open class SwitchPreferenceCompat : Preference {
    constructor(context: Context) : super(context)

    override fun setDefaultValue(value: Any?) {}
    override fun setOnPreferenceChangeListener(listener: (Preference, Any?) -> Boolean) {}
}
