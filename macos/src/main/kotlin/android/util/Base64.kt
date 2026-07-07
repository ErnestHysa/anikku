package android.util

/**
 * Stub for android.util.Base64 on macOS desktop.
 * Delegates to java.util.Base64.
 */
object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2
    const val NO_PADDING = 1
    const val URL_SAFE = 8

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        return java.util.Base64.getEncoder().encodeToString(input)
    }

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        return java.util.Base64.getDecoder().decode(str)
    }
}
