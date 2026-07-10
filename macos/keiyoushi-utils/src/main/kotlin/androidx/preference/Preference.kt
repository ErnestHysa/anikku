package androidx.preference

import android.content.Context

open class Preference(val context: Context) {
    var key: String? = null
    var title: String? = null
    var summary: String? = null
    private var _enabled: Boolean = true

    fun isEnabled(): Boolean = _enabled
    fun setEnabled(enabled: Boolean) { this._enabled = enabled }
}
