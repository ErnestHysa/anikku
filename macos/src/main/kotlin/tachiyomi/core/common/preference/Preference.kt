package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Local stub interface for Preference.
 * In Phase 2, delete this file and use the real interface from the shared core/common module.
 */
interface Preference<T> {
    fun key(): String
    fun get(): T
    fun set(value: T)
    fun isSet(): Boolean
    fun delete()
    fun defaultValue(): T
    fun changes(): Flow<T>
    fun stateIn(scope: CoroutineScope): StateFlow<T>
}
