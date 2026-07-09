package android.view

/**
 * Stub for `android.view.View`.
 */
open class View {
    companion object {
        const val VISIBLE: Int = 0
        const val INVISIBLE: Int = 4
        const val GONE: Int = 8

        const val NO_ID: Int = -1

        // MeasureSpec constants
        const val MEASURED_SIZE_MASK: Int = 0x00ffffff
        const val MEASURED_STATE_TOO_SMALL: Int = 0x01000000
        const val MEASURED_HEIGHT_STATE_SHIFT: Int = 16
    }
}
