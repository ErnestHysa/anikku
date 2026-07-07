package android.media

import android.content.Context
import android.net.Uri

/**
 * Stub for android.media.MediaScannerConnection on macOS desktop.
 */
object MediaScannerConnection {
    fun scanFile(context: Context, paths: Array<String>, mimeTypes: Array<String>?, callback: ((String, Uri?) -> Unit)?) {
        paths.forEach { callback?.invoke(it, null) }
    }
}
