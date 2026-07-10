package keiyoushi.utils

import androidx.preference.Preference
import androidx.preference.PreferenceScreen

/**
 * JVM stubs for the preference screen extension functions defined in keiyoushi's core module.
 *
 * The real implementations (in extensions-source/core/) use AndroidX `ListPreference` and
 * `SwitchPreference` to build an Android-style settings UI. On macOS/JVM those widgets
 * don't exist, so these are no-ops that just let the extension compile.
 */
fun PreferenceScreen.addListPreference(
    key: String,
    default: String,
    title: String,
    summary: String,
    entries: List<String>,
    entryValues: List<String>,
    restartRequired: Boolean = false,
    enabled: Boolean = true,
    onChange: (Preference, String) -> Boolean = { _, _ -> true },
    onComplete: (String) -> Unit = {},
) {}

fun PreferenceScreen.addSwitchPreference(
    key: String,
    default: Boolean,
    title: String,
    summary: String,
    restartRequired: Boolean = false,
    enabled: Boolean = true,
    onChange: (Preference, Boolean) -> Boolean = { _, _ -> true },
    onComplete: (Boolean) -> Unit = {},
) {}
