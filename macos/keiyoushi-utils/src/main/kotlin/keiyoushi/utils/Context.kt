package keiyoushi.utils

import android.content.Context
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Injekt-scoped application context holder.
 *
 * On Android this returned an android.app.Application instance registered in Injekt.
 * On JVM/macOS, no Application binding exists, so this provides a Context default.
 * Extensions that need app-level state should use Injekt or Koin injection directly.
 */
val applicationContext: Context
    get() = try {
        Injekt.get<Context>()
    } catch (_: Exception) {
        // No-op: use a plain Context instance as fallback
        Context()
    }
