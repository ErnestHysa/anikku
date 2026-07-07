package eu.kanade.tachiyomi.source

/**
 * Factory interface for creating sources at runtime.
 * Extensions can implement this to register multiple sources from a single JAR.
 */
interface SourceFactory {
    fun createSources(): List<Source>
}
