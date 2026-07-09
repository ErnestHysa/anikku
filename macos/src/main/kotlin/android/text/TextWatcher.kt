@file:Suppress("unused")

package android.text

/**
 * Stub for android.text.TextWatcher — used by some keiyoushi extensions.
 */
interface TextWatcher {
    fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    fun afterTextChanged(s: Editable?) {}
}
