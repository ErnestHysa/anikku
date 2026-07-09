package android.util

/**
 * Stub for `android.util.Base64` on macOS JVM.
 */
object Base64 {
    const val DEFAULT: Int = 0
    const val NO_PADDING: Int = 1
    const val NO_WRAP: Int = 2
    const val CRLF: Int = 4
    const val URL_SAFE: Int = 8
    const val NO_CLOSE: Int = 16

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String = ""

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray = input

    @JvmStatic
    fun encode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray =
        input.copyOfRange(offset, offset + len)

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray = byteArrayOf()

    @JvmStatic
    fun decode(str: ByteArray, flags: Int): ByteArray = byteArrayOf()
}
