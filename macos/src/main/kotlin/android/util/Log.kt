package android.util

/**
 * Stub for android.util.Log on macOS desktop.
 * Redirects to SLF4J/println.
 */
object Log {
    @JvmStatic
    fun d(tag: String, msg: String): Int {
        println("DEBUG/$tag: $msg")
        return 0
    }

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable): Int {
        println("DEBUG/$tag: $msg")
        tr.printStackTrace()
        return 0
    }

    @JvmStatic
    fun i(tag: String, msg: String): Int {
        println("INFO/$tag: $msg")
        return 0
    }

    @JvmStatic
    fun w(tag: String, msg: String): Int {
        println("WARN/$tag: $msg")
        return 0
    }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable): Int {
        println("WARN/$tag: $msg")
        tr.printStackTrace()
        return 0
    }

    @JvmStatic
    fun e(tag: String, msg: String): Int {
        println("ERROR/$tag: $msg")
        return 0
    }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable): Int {
        println("ERROR/$tag: $msg")
        tr.printStackTrace()
        return 0
    }

    @JvmStatic
    fun println(priority: Int, tag: String, msg: String): Int {
        println("$tag: $msg")
        return 0
    }

    @JvmStatic
    fun getStackTraceString(tr: Throwable): String {
        return tr.stackTraceToString()
    }
}
