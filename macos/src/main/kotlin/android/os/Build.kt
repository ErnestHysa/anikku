package android.os

/**
 * Stub for android.os.Build on macOS desktop.
 * Provides enough API surface for shared code compilation.
 * On desktop, all version checks pass (SDK_INT = Int.MAX_VALUE).
 */
object Build {
    const val MANUFACTURER = "Apple"
    const val MODEL = "Mac"
    const val DEVICE = "Mac"
    const val PRODUCT = "macOS"
    const val BRAND = "Apple"
    const val DISPLAY = "macOS"

    object VERSION {
        val RELEASE = System.getProperty("os.version") ?: "15.0"
        const val SDK_INT = Int.MAX_VALUE
        const val SDK_INT_NAME = "DESKTOP"

        object CODES {
            const val P = 28
            const val Q = 29
            const val R = 30
            const val S = 31
            const val TIRAMISU = 33
            const val UPSIDE_DOWN_CAKE = 34
            const val VANILLA_ICE_CREAM = 35
        }
    }

    val SUPPORTED_ABIS: Array<String> = arrayOf("arm64-v8a") // macOS Apple Silicon default

    @Suppress("DEPRECATION")
    val SUPPORTED_64_BIT_ABIS: Array<String> = arrayOf("arm64-v8a", "x86_64")
}
