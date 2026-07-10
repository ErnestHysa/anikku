package androidx.preference

import android.content.Context

open class EditTextPreference : Preference {
    constructor(context: Context) : super(context)

    var dialogTitle: String? = null
    var dialogMessage: String? = null

    fun setDefaultValue(value: Any?) {}
    fun setOnBindEditTextListener(listener: (android.widget.EditText) -> Unit) {}
    fun setOnPreferenceChangeListener(listener: (Preference, Any?) -> Boolean) {}
}
