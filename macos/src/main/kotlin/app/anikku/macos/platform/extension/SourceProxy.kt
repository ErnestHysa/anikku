package app.anikku.macos.platform.extension

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.source.CatalogueSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import java.lang.reflect.Method
import java.lang.reflect.Proxy

private val logger = KotlinLogging.logger {}

/**
 * Creates a [CatalogueSource] (or [Source]) that delegates all calls via reflection
 * to a loaded extension instance whose class implements the REAL source-api interfaces
 * (not the macOS stubs).
 *
 * This solves the class-identity problem: real extension JARs implement
 * `eu.kanade.tachiyomi.animesource.AnimeCatalogueSource` from the full source-api,
 * which is a DIFFERENT class from our stub. By using reflection, we can still
 * call its methods regardless of which class loaded them.
 *
 * ## How suspend functions are handled
 *
 * Kotlin `suspend fun` compiles to JVM methods with an extra `Continuation` parameter.
 * This proxy:
 * 1. Looks up the method on the loaded instance by name
 * 2. Creates a no-op [Continuation] that captures the return value
 * 3. Calls `Method.invoke(instance, args..., continuation)`
 * 4. Handles `COROUTINE_SUSPENDED` by resuming synchronously
 *
 * ## Supported methods
 *
 * | Method | Reflection dispatch |
 * |---|---|
 * | `getAnimeDetails(SAnime)` | Reflective → real extension |
 * | `getEpisodeList(SAnime)` | Reflective → real extension |
 * | `getVideoList(SEpisode)` | Reflective → real extension |
 * | `getPopularAnime(Int)` | Reflective → real extension |
 * | `getSearchAnime(Int, String, AnimeFilterList)` | Reflective (3-param first, 2-param fallback) |
 * | `getLatestUpdates(Int)` | Reflective → real extension |
 * | `getFilterList()` | Reflective → real extension (fallback: empty AnimeFilterList) |
 */
class ReflectiveSourceProxy(
    /** The loaded extension instance (e.g., a Gogoanime source). */
    private val delegate: Any,
) : CatalogueSource {

    private val delegateClass: Class<*> = delegate.javaClass

    override val id: Long
        get() = reflectiveGet<Long>("id") ?: delegateClass.name.hashCode().toLong()

    override val name: String
        get() = reflectiveGet<String>("name") ?: delegateClass.simpleName

    override val lang: String
        get() = reflectiveGet<String>("lang") ?: ""

    override val supportsLatest: Boolean
        get() = reflectiveGet<Boolean>("supportsLatest") ?: false

    override fun getFilterList(): AnimeFilterList {
        return try {
            val method = delegateClass.getMethod("getFilterList")
            method.invoke(delegate) as AnimeFilterList
        } catch (_: NoSuchMethodException) {
            AnimeFilterList()
        }
    }

    // -------------------------------------------------------------------------
    // Suspend method delegation
    // -------------------------------------------------------------------------

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return reflectiveCallSuspend("getAnimeDetails", arrayOf(SAnime::class.java), anime)
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return reflectiveCallSuspend("getEpisodeList", arrayOf(SAnime::class.java), anime)
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return try {
            reflectiveCallSuspend("getVideoList", arrayOf(SEpisode::class.java), episode)
        } catch (e: LinkageError) {
            // Fallback for extensions where getVideoList(SEpisode) throws NoSuchMethodError
            // or NoClassDefFoundError (e.g., the compiled AllAnime extension's internal
            // implementation references methods/classes that don't exist in the JVM classpath).
            // Use the hoster-based flow instead: getHosterList → getVideoList(Hoster) for each.
            logger.warn { "getVideoList(SEpisode) failed with ${e::class.simpleName} on ${delegateClass.name}: ${e.message}. Falling back to hoster-based flow." }
            getVideoListViaHoster(episode)
        }
    }

    /**
     * Fallback: get videos via the hoster-based flow.
     * Calls [AnimeSource.getHosterList] then [AnimeSource.getVideoList] for each hoster.
     */
    private suspend fun getVideoListViaHoster(episode: SEpisode): List<Video> {
        val hosters: List<Hoster> = try {
            reflectiveCallSuspend("getHosterList", arrayOf(SEpisode::class.java), episode)
        } catch (e: LinkageError) {
            logger.warn { "getHosterList linkage error on ${delegateClass.name}: ${e::class.simpleName}: ${e.message}" }
            emptyList()
        } catch (e: NoSuchMethodException) {
            logger.warn { "getHosterList not found on ${delegateClass.name}" }
            emptyList()
        } catch (e: Exception) {
            logger.warn { "getHosterList failed on ${delegateClass.name}: ${e::class.simpleName}: ${e.message}" }
            emptyList()
        }

        if (hosters.isEmpty()) return emptyList()

        val allVideos = mutableListOf<Video>()
        for (hoster in hosters) {
            try {
                val videos: List<Video> = reflectiveCallSuspend(
                    "getVideoList",
                    arrayOf(Hoster::class.java),
                    hoster,
                )
                allVideos.addAll(videos)
            } catch (e: Exception) {
                logger.warn { "getVideoList(Hoster) failed for hoster ${hoster.hosterName}: ${e.message}" }
            }
        }

        logger.info { "Fetched ${allVideos.size} video(s) via hoster-based fallback from ${hosters.size} hoster(s)" }
        return allVideos
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        return reflectiveCallSuspend("getPopularAnime", arrayOf(Int::class.javaPrimitiveType!!), page)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        // Try 3-param version (new source-api) first, fall back to 2-param (old extensions)
        val result = tryFindSuspendMethod("getSearchAnime", arrayOf(Int::class.javaPrimitiveType!!, String::class.java, AnimeFilterList::class.java))?.let { method ->
            @Suppress("UNCHECKED_CAST")
            reflectiveCallSuspendWithMethod(method, page, query, filters) as AnimesPage
        }
        if (result != null) return result
        // Fallback: call 2-param version (older extensions that don't use filters)
        return reflectiveCallSuspend("getSearchAnime", arrayOf(Int::class.javaPrimitiveType!!, String::class.java), page, query)
    }

    // -------------------------------------------------------------------------
    // RxJava stubs (required by CatalogueSource — will never be called via reflection)
    // -------------------------------------------------------------------------

    @Deprecated("Use suspend API", ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int): rx.Observable<AnimesPage> =
        throw UnsupportedOperationException("Use suspend API instead")

    @Deprecated("Use suspend API", ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): rx.Observable<AnimesPage> =
        throw UnsupportedOperationException("Use suspend API instead")

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        return reflectiveCallSuspend("getLatestUpdates", arrayOf(Int::class.javaPrimitiveType!!), page)
    }

    @Deprecated("Use suspend API", ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): rx.Observable<AnimesPage> =
        throw UnsupportedOperationException("Use suspend API instead")

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    /**
     * Read a property value via reflection.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> reflectiveGet(propertyName: String): T? {
        return try {
            // Try Kotlin-style getter first (no-arg method named getXxx)
            val getterName = "get${propertyName.replaceFirstChar { it.uppercase() }}"
            val method = findMethodByName(getterName, 0)
            if (method != null) {
                method.invoke(delegate) as T
            } else {
                // Fall back to field access
                val field = delegateClass.getDeclaredField(propertyName)
                field.isAccessible = true
                field.get(delegate) as T
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read property $propertyName from ${delegateClass.name}" }
            null
        }
    }

    /**
     * Call a suspend function via reflection.
     *
     * Kotlin suspend functions have JVM signature: `method(args..., Continuation): Any?`
     * where the return value is either:
     * - `COROUTINE_SUSPENDED` (a sentinel value) — the function will resume the continuation
     * - The actual result value (if it completed synchronously)
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> reflectiveCallSuspend(
        methodName: String,
        paramTypes: Array<Class<*>>,
        vararg args: Any?,
    ): T {
        val method = findSuspendMethod(methodName, paramTypes)
            ?: throw NoSuchMethodException("$methodName with ${paramTypes.size} params not found on ${delegateClass.name}")

        @Suppress("UNCHECKED_CAST")
        return suspendCoroutineUninterceptedOrReturn { continuation ->
            // Build the argument list: original args + Continuation parameter
            val callArgs = arrayOfNulls<Any?>(args.size + 1)
            args.forEachIndexed { i, arg -> callArgs[i] = arg }
            callArgs[args.size] = continuation as Continuation<*>

            try {
                val result = method.invoke(delegate, *callArgs)
                if (result === COROUTINE_SUSPENDED) {
                    // The function will resume the continuation asynchronously
                    COROUTINE_SUSPENDED
                } else {
                    // The function completed synchronously
                    result as T
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to invoke $methodName on ${delegateClass.name}", e)
            }
        }
    }

    /**
     * Find a method by name with the given parameter count (ignoring the Continuation param).
     * The continuation param is always the last parameter of a suspend function.
     */
    private fun findSuspendMethod(name: String, paramTypes: Array<Class<*>>): Method? {
        return delegateClass.methods.firstOrNull { method ->
            if (method.name != name) return@firstOrNull false
            val params = method.parameterTypes
            // Suspend functions have paramTypes.size + 1 parameters (extra Continuation)
            if (params.size != paramTypes.size + 1 && params.size != paramTypes.size) return@firstOrNull false
            // Check that the non-Continuation params match
            for (i in paramTypes.indices) {
                if (i >= params.size - 1) return@firstOrNull false
                if (!params[i].isAssignableFrom(paramTypes[i])) return@firstOrNull false
            }
            // Last param should be Continuation (or no extra param for non-suspend)
            true
        }
    }

    /**
     * Try to find a suspend method by name and param types, returning null if not found.
     */
    private fun tryFindSuspendMethod(name: String, paramTypes: Array<Class<*>>): Method? {
        return findSuspendMethod(name, paramTypes)
    }

    /**
     * Call a suspend function via reflection using a pre-resolved Method.
     * Skips the method lookup step.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> reflectiveCallSuspendWithMethod(
        method: Method,
        vararg args: Any?,
    ): T {
        return suspendCoroutineUninterceptedOrReturn { continuation ->
            val callArgs = arrayOfNulls<Any?>(args.size + 1)
            args.forEachIndexed { i, arg -> callArgs[i] = arg }
            callArgs[args.size] = continuation as Continuation<*>

            try {
                val result = method.invoke(delegate, *callArgs)
                if (result === COROUTINE_SUSPENDED) {
                    COROUTINE_SUSPENDED
                } else {
                    result as T
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to invoke ${method.name} on ${delegateClass.name}", e)
            }
        }
    }

    /**
     * Find a no-arg method by name (for property getters).
     */
    private fun findMethodByName(name: String, paramCount: Int): Method? {
        return delegateClass.methods.firstOrNull { method ->
            method.name == name && method.parameterTypes.size == paramCount
        }
    }
}

/**
 * Attempts to create a [CatalogueSource] wrapper around a loaded extension instance.
 *
 * First tries direct `instance is` check (works for test extensions compiled against stubs).
 * Falls back to [ReflectiveSourceProxy] for real extensions compiled against the full source-api.
 *
 * @return The wrapped source, or null if the instance doesn't have the expected methods.
 */
fun wrapAsSource(instance: Any): CatalogueSource? {
    // Fast path: direct type check (works for test extension compiled against stubs)
    if (instance is CatalogueSource) {
        return instance
    }
    if (instance is eu.kanade.tachiyomi.source.SourceFactory) {
        // Handle SourceFactory: try to create individual sources from it
        val sources = instance.createSources()
        // Return first source that's a CatalogueSource — or wrap the whole factory?
        // For now, return null and let the loader handle it via the factory path
        return null
    }

    // Slow path: check via reflection if the instance has the expected suspend methods
    return try {
        val clazz = instance.javaClass
        val hasGetAnimeDetails = clazz.methods.any {
            it.name == "getAnimeDetails" && it.parameterTypes.size >= 1
        }
        val hasGetEpisodeList = clazz.methods.any {
            it.name == "getEpisodeList" && it.parameterTypes.size >= 1
        }
        val hasGetVideoList = clazz.methods.any {
            it.name == "getVideoList" && it.parameterTypes.size >= 1
        }

        if (hasGetAnimeDetails && hasGetEpisodeList && hasGetVideoList) {
            logger.info { "Wrapping ${clazz.name} via ReflectiveSourceProxy" }
            ReflectiveSourceProxy(instance)
        } else {
            logger.warn { "Instance ${clazz.name} does not have the expected Source methods" }
            null
        }
    } catch (e: Exception) {
        logger.error(e) { "Failed to wrap ${instance.javaClass.name} as Source" }
        null
    }
}
