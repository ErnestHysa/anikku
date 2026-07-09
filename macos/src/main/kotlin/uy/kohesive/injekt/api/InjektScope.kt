package uy.kohesive.injekt.api

import kotlin.reflect.KClass

/**
 * Injekt scope interface for macOS desktop.
 *
 * Extensions compiled against the keiyoushi Injekt library call
 * `Injekt.getInjekt().getInstance<T>()` (inlined from `injectLazy`).
 *
 * The inlined bytecode passes `T::class` (KClass) as the argument.
 * Koin natively supports lookup by [KClass] via its `get(clazz: KClass<T>)`
 * overload, avoiding the Class/Class<?> generic type issues.
 */
interface InjektScope {
    /**
     * Resolve a dependency instance by Kotlin class.
     */
    fun <T : Any> getInstance(clazz: KClass<T>): T
}
