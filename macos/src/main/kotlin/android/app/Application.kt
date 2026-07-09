package android.app

/**
 * Stub for `android.app.Application` on macOS JVM.
 *
 * Extensions compiled for Android reference `android.app.Application` in their
 * bytecode (e.g., through inlined `injectLazy()` from `AnimeHttpSource`). On
 * Android this class is provided by the Android framework. On macOS/JVM it
 * doesn't exist, causing `NoDefinitionFoundException` when the extension tries
 * to inject it via Injekt.
 *
 * This stub provides the class so the JVM can resolve it, and a corresponding
 * Koin singleton (registered in [PlatformModule]) satisfies the injection
 * request. The stub has no functionality — it just needs to exist as a type.
 */
open class Application {
    // No-op stub — only needed for type resolution in extension bytecode
}
