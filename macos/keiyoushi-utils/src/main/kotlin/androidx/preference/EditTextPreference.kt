package androidx.preference

import android.content.Context

open class EditTextPreference : Preference {
    constructor(context: Context) : super(context)

    var text: String? = null
    var dialogTitle: String? = null
    var dialogMessage: String? = null

    override fun setDefaultValue(value: Any?) {}
    fun setOnBindEditTextListener(listener: (android.widget.EditText) -> Unit) {}
    override fun setOnPreferenceChangeListener(listener: (Preference, Any?) -> Boolean) {}
}
