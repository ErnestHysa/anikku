package uy.kohesive.injekt

import kotlin.reflect.KClass
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import uy.kohesive.injekt.api.InjektFactory
import uy.kohesive.injekt.api.InjektModule
import java.lang.reflect.Type

// NOTE: InjektFactory is defined locally (not imported from JAR)
// to avoid Kotlin compiler version conflicts with injekt-api:1.16.1

/**
 * Injekt stub for macOS desktop.
 * Delegates all dependency resolution to Koin via GlobalContext.
 * This allows existing shared code using `Injekt.get<T>()` to compile and run
 * on desktop without changes to the shared source files.
 *
 * Extensions compiled against the keiyoushi Injekt library call
 * `Injekt.getInjekt().getInstance<T>()` from inlined `injectLazy()`.
 * This file provides the top-level [getInjekt] function to satisfy
 * those call sites at runtime.
 *
 * IMPORTANT: Extensions expect [getInjekt] to return [InjektFactory] (from
 * the injekt-api JAR), NOT [InjektScope]. The [InjektFactory] interface has
 * 10 methods that extensions call via inlined bytecode. The implementation
 * below delegates all lookups to Koin's GlobalContext.
 */
object Injekt {

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> get(): T {
        return GlobalContext.get().get<T>(T::class)
    }

    fun importModule(module: InjektModule) {
        // On macOS, modules are registered directly in Koin via KoinApplication.
        // This exists for API compatibility with shared code that calls importModule.
        // See AnikkuApplication.kt for the macOS Koin module setup.
    }
}

/**
 * Internal InjektFactory implementation that delegates to Koin's GlobalContext.
 *
 * Extensions compiled with keiyoushi's Injekt library call
 * `InjektKt.getInjekt().getInstance<NetworkHelper>()` from their inlined
 * `injectLazy()`. The inlined bytecode casts the result to [InjektFactory]
 * and calls [InjektFactory.getInstance] with a [Type] argument.
 */
private val macosInjektScope = object : InjektFactory {

    /**
     * Convert a [Type] to a [KClass] for Koin lookup.
     */
    private fun resolveToKClass(type: Type): KClass<*> {
        @Suppress("UNCHECKED_CAST")
        val cls: Class<*> = when (type) {
            is Class<*> -> type
            is java.lang.reflect.ParameterizedType -> type.rawType as Class<*>
            else -> Any::class.java
        }
        // kotlin extension property on Class<T> (from kotlin-stdlib)
        return cls.kotlin
    }

    /** Resolve via Koin using the Type. */
    @Suppress("UNCHECKED_CAST")
    private fun <R> resolve(type: Type): R {
        return GlobalContext.get().get(resolveToKClass(type)) as R
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getInstance(type: Type): R {
        return resolve(type)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getInstanceOrElse(type: Type, default: R): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            default
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getInstanceOrElse(type: Type, defaultProvider: () -> R): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            defaultProvider()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getInstanceOrNull(type: Type): R? {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, K> getKeyedInstance(type: Type, key: K): R {
        return GlobalContext.get().get(resolveToKClass(type), named(key.toString())) as R
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, K> getKeyedInstanceOrElse(type: Type, key: K, default: R): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type), named(key.toString())) as R
        } catch (_: Exception) {
            default
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, K> getKeyedInstanceOrElse(type: Type, key: K, defaultProvider: () -> R): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type), named(key.toString())) as R
        } catch (_: Exception) {
            defaultProvider()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, K> getKeyedInstanceOrNull(type: Type, key: K): R? {
        return try {
            GlobalContext.get().get(resolveToKClass(type), named(key.toString())) as R
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> getLogger(type: Type, name: String): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            @Suppress("UNCHECKED_CAST")
            val noop = Any() as R
            noop
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R, T> getLogger(type: Type, clazz: Class<T>): R {
        return try {
            GlobalContext.get().get(resolveToKClass(type)) as R
        } catch (_: Exception) {
            @Suppress("UNCHECKED_CAST")
            val noop = Any() as R
            noop
        }
    }
}

/**
 * Top-level function that provides the InjektFactory for extension DI resolution.
 * Extensions call this as `InjektKt.getInjekt()` from their inlined `injectLazy()`.
 *
 * NOTE: Must return [InjektFactory] (from injekt-api JAR), NOT our local [InjektScope] interface.
 * The extension bytecode casts the result to InjektFactory at the call site.
 */
fun getInjekt(): InjektFactory = macosInjektScope

/**
 * No-op on desktop. On Android, this patches Injekt for compatibility with older versions.
 */
fun patchInjekt() {
    // No-op on macOS desktop
}
