package android.widget

/**
 * Stub for android.widget.Toast on macOS desktop.
 * Prints to stdout instead of showing a toast popup.
 */
open class Toast private constructor() {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @JvmStatic
        fun makeText(context: android.content.Context, text: CharSequence, duration: Int): Toast {
            println("TOAST: $text")
            return Toast()
        }

        @JvmStatic
        fun makeText(context: android.content.Context, resId: Int, duration: Int): Toast {
            println("TOAST: resId=$resId")
            return Toast()
        }
    }

    open fun show() {}
    open fun cancel() {}
}
