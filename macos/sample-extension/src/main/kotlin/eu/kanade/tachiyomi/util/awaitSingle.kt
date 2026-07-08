package eu.kanade.tachiyomi.util

import rx.Observable

/**
 * macOS stub of the RxJava awaitSingle extension.
 * Blocks the current thread and returns the single item from the observable.
 */
fun <T> Observable<T>.awaitSingle(): T {
    return this.toBlocking().single()
}
