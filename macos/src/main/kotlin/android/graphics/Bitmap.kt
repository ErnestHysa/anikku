package android.graphics

/**
 * Stubs for android.graphics on macOS desktop.
 * Minimal API surface — full port requires Compose Desktop equivalents.
 */
open class Bitmap {
    open val width: Int get() = 0
    open val height: Int get() = 0

    open fun recycle() {}

    data class Config(val name: String) {
        companion object {
            val ARGB_8888 = Config("ARGB_8888")
            val RGB_565 = Config("RGB_565")
            val ALPHA_8 = Config("ALPHA_8")
        }
    }
}

class BitmapFactory {
    companion object {
        fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = Bitmap()
        fun decodeFile(pathName: String): Bitmap? = Bitmap()
        fun decodeStream(inputStream: java.io.InputStream?): Bitmap? = Bitmap()
    }
}

class Canvas {
    fun drawColor(color: Int) {}
    fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {}
    fun drawRect(rect: Rect, paint: Paint) {}
}

open class Paint {
    open var color: Int = 0
    open var alpha: Int = 255
    open var isAntiAlias: Boolean = false
    open var style: Paint.Style = Style.FILL
    open var textSize: Float = 12f

    enum class Style { FILL, STROKE }
}

class Rect {
    var left: Int = 0
    var top: Int = 0
    var right: Int = 0
    var bottom: Int = 0
}

class Matrix

class BitmapRegionDecoder {
    companion object {
        fun newInstance(data: ByteArray, offset: Int, length: Int, isShareable: Boolean): BitmapRegionDecoder? =
            BitmapRegionDecoder()
        fun newInstance(path: String, isShareable: Boolean): BitmapRegionDecoder? =
            BitmapRegionDecoder()
    }
}
