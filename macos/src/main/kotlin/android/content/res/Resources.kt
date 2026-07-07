package android.content.res

/**
 * Stub for android.content.res.Resources on macOS desktop.
 */
open class Resources {
    open fun getString(id: Int): String = ""
    open fun getString(id: Int, vararg formatArgs: Any?): String = ""
    open fun getDimension(id: Int): Float = 0f
    open fun getDimensionPixelSize(id: Int): Int = 0
    open fun getBoolean(id: Int): Boolean = false
    open fun getInteger(id: Int): Int = 0
    open fun getColor(id: Int, theme: Resources.Theme? = null): Int = 0
    open fun getIntArray(id: Int): IntArray = intArrayOf()
    open fun getStringArray(id: Int): Array<String> = emptyArray()
    open val displayMetrics: DisplayMetrics get() = DisplayMetrics()
    open val configuration: Configuration get() = Configuration()

    open class Theme
}

class DisplayMetrics {
    var density: Float = 1.0f
    var densityDpi: Int = 160
    var widthPixels: Int = 1920
    var heightPixels: Int = 1080
    var scaledDensity: Float = 1.0f
}

class Configuration {
    var orientation: Int = ORIENTATION_PORTRAIT
    var screenLayout: Int = 0
    var uiMode: Int = 0
    var fontScale: Float = 1.0f

    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
        const val UI_MODE_NIGHT_MASK = 0x30
        const val UI_MODE_NIGHT_YES = 0x20
        const val SCREENLAYOUT_SIZE_MASK = 0x0f
        const val SCREENLAYOUT_SIZE_XLARGE = 0x04
    }
}
