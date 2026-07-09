package keiyoushi.utils

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Injekt-scoped application context holder.
 *
 * On Android this returned an android.app.Application instance registered in Injekt.
 * On JVM/macOS, no Application binding exists, so this provides a simple Any default.
 * Extensions that need app-level state should use Injekt or Koin injection directly.
 */
val applicationContext: Any
    get() = try {
        Injekt.get<Any>()
    } catch (_: Exception) {
        Any()
    }
