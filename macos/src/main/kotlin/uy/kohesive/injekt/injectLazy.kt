package uy.kohesive.injekt

/**
 * Injekt lazy injection delegate stub for macOS desktop.
 * Delegates to Koin's GlobalContext.get() for lazy resolution.
 *
 * Usage: `val foo: Foo by injectLazy()`
 */
inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { Injekt.get<T>() }
