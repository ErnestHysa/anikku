package android.net

import java.net.URI

/**
 * Stub for android.net.Uri on macOS desktop.
 * Wraps java.net.URI for source compatibility.
 */
class Uri private constructor(private val uri: URI) {

    override fun toString(): String = uri.toString()

    fun toJavaUri(): URI = uri

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri = Uri(URI(uriString))

        @JvmStatic
        fun fromJavaUri(javaUri: URI): Uri = Uri(javaUri)
    }
}
