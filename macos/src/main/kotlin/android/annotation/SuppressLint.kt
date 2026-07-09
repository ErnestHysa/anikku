package android.annotation

/**
 * Stub for `android.annotation.SuppressLint` on macOS JVM.
 *
 * Compile-only annotation — no runtime behavior needed.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD)
annotation class SuppressLint(vararg val value: String)
