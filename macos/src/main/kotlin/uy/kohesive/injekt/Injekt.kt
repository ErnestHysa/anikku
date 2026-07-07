package uy.kohesive.injekt

import uy.kohesive.injekt.api.InjektModule

/**
 * Injekt stub for macOS desktop.
 * Delegates all dependency resolution to Koin via GlobalContext.
 * This allows existing shared code using `Injekt.get<T>()` to compile and run
 * on desktop without changes to the shared source files.
 */
object Injekt {

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> get(): T {
        return org.koin.core.context.GlobalContext.get().get<T>(T::class)
    }

    fun importModule(module: InjektModule) {
        // On macOS, modules are registered directly in Koin via KoinApplication.
        // This exists for API compatibility with shared code that calls importModule.
        // See AnikkuApplication.kt for the macOS Koin module setup.
    }
}

/**
 * No-op on desktop. On Android, this patches Injekt for compatibility with older versions.
 */
fun patchInjekt() {
    // No-op on macOS desktop
}
