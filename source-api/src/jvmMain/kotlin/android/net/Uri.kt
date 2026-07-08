package android.net

import java.io.File

actual open class Uri {
    actual val scheme: String? = null
    actual val path: String? = null

    actual companion object {
        actual fun fromFile(file: File): Uri = Uri()
        actual fun parse(uriString: String): Uri = Uri()
    }
}
