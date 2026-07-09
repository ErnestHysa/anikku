package keiyoushi.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.logging.Logger

private val coroutinesLogger = Logger.getLogger("keiyoushi.utils.Coroutines")

/**
 * Maps elements in parallel using [Dispatchers.IO].
 *
 * @since extensions-lib 14
 */
suspend fun <T, R> Iterable<T>.parallelMap(transform: suspend (T) -> R): List<R> = coroutineScope {
    map { async(Dispatchers.IO) { transform(it) } }.awaitAll()
}

/**
 * Blocking version of [parallelMap].
 *
 * @since extensions-lib 14
 */
fun <T, R> Iterable<T>.parallelMapBlocking(transform: (T) -> R): List<R> = runBlocking {
    parallelMap { transform(it) }
}

/**
 * Maps not-null elements in parallel using [Dispatchers.IO].
 *
 * @since extensions-lib 14
 */
suspend fun <T, R : Any> Iterable<T>.parallelMapNotNull(transform: suspend (T) -> R?): List<R> =
    coroutineScope {
        map { async(Dispatchers.IO) { transform(it) } }.awaitAll().filterNotNull()
    }

/**
 * Blocking version of [parallelMapNotNull].
 *
 * @since extensions-lib 14
 */
fun <T, R : Any> Iterable<T>.parallelMapNotNullBlocking(transform: (T) -> R?): List<R> = runBlocking {
    parallelMapNotNull { transform(it) }
}

/**
 * Maps and flattens in parallel using [Dispatchers.IO].
 *
 * @since extensions-lib 14
 */
suspend fun <T, R> Iterable<T>.parallelFlatMap(transform: suspend (T) -> Iterable<R>): List<R> =
    coroutineScope {
        map { async(Dispatchers.IO) { transform(it) } }.awaitAll().flatten()
    }

/**
 * Blocking version of [parallelFlatMap].
 *
 * @since extensions-lib 14
 */
fun <T, R> Iterable<T>.parallelFlatMapBlocking(transform: (T) -> Iterable<R>): List<R> = runBlocking {
    parallelFlatMap { transform(it) }
}

/**
 * Like [flatMap] but catches errors, logging them and returning an empty list for failed items.
 *
 * @since extensions-lib 14
 */
suspend fun <T, R> Iterable<T>.parallelCatchingFlatMap(
    errorMessage: String = "An error occurred in parallelCatchingFlatMap",
    transform: suspend (T) -> Iterable<R>,
): List<R> = coroutineScope {
    map { item ->
        async(Dispatchers.IO) {
            try {
                transform(item)
            } catch (e: Exception) {
                coroutinesLogger.warning("$errorMessage: ${e.message}")
                emptyList()
            }
        }
    }.awaitAll().flatten()
}

/**
 * Blocking version of [parallelCatchingFlatMap].
 *
 * @since extensions-lib 14
 */
fun <T, R> Iterable<T>.parallelCatchingFlatMapBlocking(
    errorMessage: String = "An error occurred in parallelCatchingFlatMap",
    transform: (T) -> Iterable<R>,
): List<R> = runBlocking {
    parallelCatchingFlatMap(errorMessage) { transform(it) }
}

/**
 * Like [mapNotNull] but catches errors, logging them and returning null for failed items.
 *
 * @since extensions-lib 14
 */
suspend fun <T, R : Any> Iterable<T>.parallelCatchingMapNotNull(
    errorMessage: String = "An error occurred in parallelCatchingMapNotNull",
    transform: suspend (T) -> R?,
): List<R> = coroutineScope {
    map { item ->
        async(Dispatchers.IO) {
            try {
                transform(item)
            } catch (e: Exception) {
                coroutinesLogger.warning("$errorMessage: ${e.message}")
                null
            }
        }
    }.awaitAll().filterNotNull()
}

/**
 * Like [flatMap] but catches errors sequentially, logging them and continuing.
 *
 * @since extensions-lib 14
 */
suspend fun <T, R> Iterable<T>.catchingFlatMap(
    errorMessage: String = "An error occurred in catchingFlatMap",
    transform: suspend (T) -> Iterable<R>,
): List<R> {
    val result = mutableListOf<R>()
    for (item in this) {
        try {
            result.addAll(transform(item))
        } catch (e: Exception) {
            coroutinesLogger.warning("$errorMessage: ${e.message}")
        }
    }
    return result
}

/**
 * Blocking version of [catchingFlatMap].
 *
 * @since extensions-lib 14
 */
fun <T, R> Iterable<T>.catchingFlatMapBlocking(
    errorMessage: String = "An error occurred in catchingFlatMap",
    transform: (T) -> Iterable<R>,
): List<R> = runBlocking {
    catchingFlatMap(errorMessage) { transform(it) }
}

/**
 * Like [flatMap] but catches errors synchronously, logging them and continuing.
 *
 * @since extensions-lib 14
 */
fun <T, R> Iterable<T>.flatMapCatching(
    errorMessage: String = "An error occurred in flatMapCatching",
    transform: (T) -> Iterable<R>,
): List<R> {
    val result = mutableListOf<R>()
    for (item in this) {
        try {
            result.addAll(transform(item))
        } catch (e: Exception) {
            coroutinesLogger.warning("$errorMessage: ${e.message}")
        }
    }
    return result
}
