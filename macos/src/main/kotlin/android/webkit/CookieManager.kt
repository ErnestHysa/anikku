package android.webkit

/**
 * Stub for android.webkit.CookieManager on macOS desktop.
 * Provides a simple in-memory cookie store.
 */
open class CookieManager {

    private val cookies = mutableMapOf<String, String>()

    open fun setCookie(url: String?, value: String?) {
        if (url != null && value != null) {
            cookies[url] = value
        }
    }

    open fun getCookie(url: String?): String? {
        return url?.let { cookies[it] }
    }

    open fun removeAllCookies(callback: (() -> Unit)?) {
        cookies.clear()
        callback?.invoke()
    }

    open fun removeAllCookies() {
        cookies.clear()
    }

    open fun flush() {}

    open fun removeSessionCookies(callback: (() -> Unit)?) {
        callback?.invoke()
    }

    companion object {
        @JvmStatic
        fun getInstance(): CookieManager = instance

        private val instance = CookieManager()
    }
}
