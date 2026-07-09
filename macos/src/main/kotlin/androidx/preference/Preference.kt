@file:Suppress("unused")

package androidx.preference

/**
 * Stub for android.preference.Preference used by compiled keiyoushi extensions.
 *
 * Keiyoushi extensions are compiled against Android's Preference library for
 * configuration screens. On macOS, preferences are managed through
 * [app.anikku.macos.platform.preference.MacOSPreferenceStore] instead.
 *
 * These stubs prevent NoClassDefFoundError when the extension JAR references
 * Preference classes during class loading.
 */
open class Preference(
    context: android.content.Context?,
    attrs: kotlinx.collections.immutable.PersistentMap<String, String>? = null,
) {

    var key: String? = null
    var title: String? = null
    var summary: String? = null
    var defaultValue: String? = null
    var isPersistent: Boolean = false

    open fun getOnPreferenceChangeListener(): OnPreferenceChangeListener? = null

    open fun setOnPreferenceChangeListener(listener: OnPreferenceChangeListener?) {}

    fun interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
    }
}

/**
 * Stub for EditTextPreference — text-based preference entry.
 */
open class EditTextPreference(context: android.content.Context?) : Preference(context) {
    var text: String? = null
}

/**
 * Stub for ListPreference — dropdown/radio preference entry.
 */
open class ListPreference(context: android.content.Context?) : Preference(context) {
    var entries: Array<String>? = null
    var entryValues: Array<String>? = null
    var value: String? = null
}

/**
 * Stub for SwitchPreference — toggle preference entry.
 */
open class SwitchPreference(context: android.content.Context?) : Preference(context) {
    var isChecked: Boolean = false
}

/**
 * Stub for CheckBoxPreference — checkbox preference entry.
 */
open class CheckBoxPreference(context: android.content.Context?) : Preference(context) {
    var isChecked: Boolean = false
}

/**
 * Stub for PreferenceScreen — root of a preference hierarchy.
 */
open class PreferenceScreen(context: android.content.Context?) : PreferenceGroup(context)

/**
 * Stub for PreferenceGroup — container for multiple preferences.
 */
open class PreferenceGroup(context: android.content.Context?) : Preference(context) {
    private val _preferences = mutableListOf<Preference>()

    fun addPreference(preference: Preference): Boolean = _preferences.add(preference)
    fun removePreference(preference: Preference): Boolean = _preferences.remove(preference)
    fun getPreference(index: Int): Preference? = _preferences.getOrNull(index)
    val preferenceCount: Int get() = _preferences.size
}
