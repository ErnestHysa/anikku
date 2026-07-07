package android.annotation

/**
 * Stub for android.annotation.SuppressLint on macOS desktop.
 * Empty annotation — lint suppression is Android-specific.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.SOURCE)
annotation class SuppressLint(vararg val value: String)
