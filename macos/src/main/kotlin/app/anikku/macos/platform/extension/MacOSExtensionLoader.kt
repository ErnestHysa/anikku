package app.anikku.macos.platform.extension

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipEntry

private val logger = KotlinLogging.logger {}

/**
 * JAR-based extension loader for macOS.
 *
 * Extensions are JAR files stored in an extensions directory.
 * Each JAR must contain a `META-INF/extension.json` file with metadata.
 *
 * Replaces the Android ExtensionLoader which uses PackageManager and PathClassLoader.
 */
object MacOSExtensionLoader {

    /** Minimum and maximum supported extension lib versions */
    const val LIB_VERSION_MIN = 12
    const val LIB_VERSION_MAX = 15

    private const val EXTENSION_METADATA_PATH = "META-INF/extension.json"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Tracks active URLClassLoaders by package name for cleanup.
     */
    private val classLoaders = mutableMapOf<String, URLClassLoader>()

    /**
     * Metadata format for extension.json inside each extension JAR.
     */
    @Serializable
    data class ExtensionMetadata(
        val name: String,
        val pkgName: String,
        val versionName: String,
        val versionCode: Long,
        val libVersion: Double,
        val lang: String = "",
        val isNsfw: Boolean = false,
        val isTorrent: Boolean = false,
        val sourceClass: String,
        val pkgFactory: String? = null,
        val hasReadme: Boolean = false,
        val hasChangelog: Boolean = false,
    )

    /**
     * Trust store entry.
     */
    @Serializable
    data class TrustEntry(
        val pkgName: String,
        val versionCode: Long,
        val signatureHash: String,
    )

    /**
     * Load all extensions from the extensions directory.
     *
     * Supports both JAR files (native JVM extensions with META-INF/extension.json)
     * and APK files (keiyoushi Android extensions, automatically converted via
     * [DexClassLoader] when jadx is available).
     */
    fun loadExtensions(
        extensionsDir: File,
        trustStore: Map<String, List<TrustEntry>> = emptyMap(),
        loadNsfw: Boolean = false,
    ): List<LoadResult> {
        if (!extensionsDir.isDirectory) return emptyList()

        val files = extensionsDir.listFiles()?.filter { it.isFile } ?: emptyList()

        // Separate JAR/EXT files from APK files
        val jarFiles = files.filter { it.extension == "jar" || it.extension == "ext" }
        val apkFiles = files.filter { it.extension == "apk" }

        logger.info { "Loading ${jarFiles.size} JAR extensions, ${apkFiles.size} APK extensions from ${extensionsDir.absolutePath}" }

        val results = mutableListOf<LoadResult>()

        // Load native JVM extensions
        results.addAll(jarFiles.map { loadExtension(it, extensionsDir, trustStore, loadNsfw) })

        // Convert and load APK (keiyoushi) extensions
        if (apkFiles.isNotEmpty()) {
            convertAndLoadApks(apkFiles, extensionsDir, results, trustStore, loadNsfw)
        }

        return results
    }

    /**
     * Convert keiyoushi APK files to JARs and load them.
     * Uses [DexClassLoader] to decompile DEX bytecode via jadx + javac.
     *
     * Limitations:
     * - Requires jadx to be installed (`brew install jadx`)
     * - Obfuscated extensions (R8/ProGuard) produce decompiled code with
     *   meaningless class names — conversion may fail or produce unusable results
     * - Source-api JAR paths are resolved by DexClassLoader internally or
     *   must be provided in the extensions directory's parent structure
     */
    private fun convertAndLoadApks(
        apkFiles: List<File>,
        extensionsDir: File,
        results: MutableList<LoadResult>,
        trustStore: Map<String, List<TrustEntry>> = emptyMap(),
        loadNsfw: Boolean = false,
    ) {
        if (!DexClassLoader.isAvailable()) {
            logger.warn { "jadx not available — cannot convert ${apkFiles.size} APK extension(s). Install: brew install jadx" }
            apkFiles.forEach { apk ->
                results.add(LoadResult.Error)
            }
            return
        }

        for (apkFile in apkFiles) {
            val jarName = apkFile.nameWithoutExtension + ".jar"
            val jarFile = File(extensionsDir, jarName)

            logger.info { "Converting APK extension: ${apkFile.name} → ${jarFile.name}" }
            val success = DexClassLoader.convertToJar(apkFile, jarFile, null, null)

            if (success && jarFile.isFile) {
                logger.info { "Loading converted extension: ${jarFile.name}" }
                results.add(loadExtension(jarFile, extensionsDir, trustStore, loadNsfw))
            } else {
                logger.error { "Failed to convert APK extension: ${apkFile.name}" }
                results.add(LoadResult.Error)
            }
        }
    }

    /**
     * Load a single extension JAR.
     *
     * Trust model (security-critical):
     * - Trust is always verified if trustStore is non-empty
     * - If trustStore is empty (first run), ALL extensions are untrusted
     * - Extensions must be explicitly trusted via trustExtension() before loading
     */
    fun loadExtension(
        jarFile: File,
        libsDir: File? = null,
        trustStore: Map<String, List<TrustEntry>> = emptyMap(),
        loadNsfw: Boolean = false,
    ): LoadResult {
        val metadata = readMetadata(jarFile) ?: return LoadResult.Error

        val pkgName = metadata.pkgName

        // Validate lib version
        if (metadata.libVersion < LIB_VERSION_MIN || metadata.libVersion > LIB_VERSION_MAX) {
            logger.warn { "Lib version ${metadata.libVersion} for $pkgName outside supported range [$LIB_VERSION_MIN, $LIB_VERSION_MAX]" }
            return LoadResult.Error
        }

        // Compute JAR hash for trust verification
        val signatureHash = computeSha256(jarFile)

        // Always verify trust. If trustStore is empty, nothing is trusted.
        val trustedEntries = trustStore[pkgName]
        if (trustedEntries == null || trustedEntries.none { it.signatureHash == signatureHash }) {
            logger.warn { "Extension $pkgName is untrusted (hash: $signatureHash)" }
            return LoadResult.Untrusted(
                Extension.Untrusted(
                    name = metadata.name,
                    pkgName = metadata.pkgName,
                    versionName = metadata.versionName,
                    versionCode = metadata.versionCode,
                    libVersion = metadata.libVersion,
                    signatureHash = signatureHash,
                    lang = metadata.lang,
                    isNsfw = metadata.isNsfw,
                    isTorrent = metadata.isTorrent,
                )
            )
        }

        // Check NSFW
        if (metadata.isNsfw && !loadNsfw) {
            logger.warn { "NSFW extension $pkgName blocked by user preference" }
            return LoadResult.Error
        }

        // Close old class loader if replacing
        classLoaders.remove(pkgName)?.close()

        // Build class loader
        val classLoader = buildClassLoader(jarFile, libsDir)
        classLoaders[pkgName] = classLoader

        // Load sources (resilient: individual class failures skip, not fail the extension)
        val sources = try {
            loadSources(classLoader, metadata)
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error loading sources from $pkgName" }
            classLoaders.remove(pkgName)?.close()
            return LoadResult.Error
        }

        if (sources.isEmpty()) {
            logger.warn { "No valid sources found in $pkgName — all listed classes failed to instantiate" }
            classLoaders.remove(pkgName)?.close()
            return LoadResult.Error
        }

        logger.info { "Loaded ${sources.size} source(s) from $pkgName" }

        // Build lang from sources
        val sourceLangs = sources
            .filterIsInstance<eu.kanade.tachiyomi.source.CatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (sourceLangs.size) {
            0 -> metadata.lang
            1 -> sourceLangs.first()
            else -> "all"
        }

        return LoadResult.Success(
            Extension.Installed(
                name = metadata.name,
                pkgName = metadata.pkgName,
                versionName = metadata.versionName,
                versionCode = metadata.versionCode,
                libVersion = metadata.libVersion,
                lang = lang,
                isNsfw = metadata.isNsfw,
                isTorrent = metadata.isTorrent,
                sources = sources,
                pkgFactory = metadata.pkgFactory,
                icon = null,
                hasUpdate = false,
                isObsolete = false,
                isShared = false,
            )
        )
    }

    /**
     * Read extension metadata from the JAR's META-INF/extension.json.
     */
    fun readMetadata(jarFile: File): ExtensionMetadata? {
        return try {
            JarFile(jarFile).use { jar ->
                val entry: ZipEntry = jar.getEntry(EXTENSION_METADATA_PATH)
                    ?: return logAndNull("No $EXTENSION_METADATA_PATH in ${jarFile.name}")

                val content = jar.getInputStream(entry).use { stream: InputStream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }

                json.decodeFromString<ExtensionMetadata>(content)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to read metadata from ${jarFile.name}" }
            null
        }
    }

    /**
     * Build a URLClassLoader for the extension JAR and its dependencies.
     *
     * Shared dependency JARs (source-api, common, kotlin-stdlib, etc.) are added
     * DIRECTLY to the URLClassLoader's URLs so extensions can find them regardless
     * of class loader parent delegation behavior. This is the most robust approach
     * because:
     *
     * 1. The parent class loader may not expose all app dependencies (Gradle's
     *    class loader hierarchy is complex and varies by launch method).
     * 2. Android extensions use `compileOnly` for these libraries — they expect
     *    the host to provide them at runtime.
     * 3. Adding JARs directly to the URLClassLoader URLs bypasses all delegation
     *    issues and guarantees the classes are available.
     */
    private fun buildClassLoader(jarFile: File, libsDir: File?): URLClassLoader {
        val urls = mutableListOf(jarFile.toURI().toURL())

        // 1. Scan the extension's own libs/ directory for dependency JARs
        val depsDir = libsDir?.let { File(it, "libs") } ?: File(jarFile.parentFile, "libs")
        if (depsDir.isDirectory) {
            depsDir.listFiles()
                ?.filter { it.extension == "jar" }
                ?.forEach { urls.add(it.toURI().toURL()) }
        }

        // 2. Add shared dependency JARs from the macOS app's libs/ directory.
        //    These are built by the `rebuildSourceApiJars` Gradle task and
        //    contain the source-api and core/common classes that extensions
        //    reference via `compileOnly` (ConfigurableAnimeSource, AnimeSource, etc.).
        val sharedLibsDir = findSharedLibsDir()
        if (sharedLibsDir.isDirectory) {
            sharedLibsDir.listFiles()
                ?.filter { it.extension == "jar" }
                ?.forEach { urls.add(it.toURI().toURL()) }
        }

        // 3. Also scan extensions/libs/ for globally shared dependency JARs
        val globalLibsDir = File(jarFile.parentFile, "libs")
        if (globalLibsDir.isDirectory && globalLibsDir != depsDir) {
            globalLibsDir.listFiles()
                ?.filter { it.extension == "jar" }
                ?.forEach { urls.add(it.toURI().toURL()) }
        }

        return URLClassLoader(
            urls.toTypedArray(),
            MacOSExtensionLoader::class.java.classLoader,
        )
    }

    /**
     * Find the directory containing shared library JARs (source-api-jvm.jar, common-jvm.jar, etc.).
     *
     * Search order:
     * 1. `macos/libs/` relative to working directory (development via gradlew run)
     * 2. `../libs/` relative to app bundle Contents (packaged .app)
     * 3. `libs/` relative to working directory (any other setup)
     */
    private fun findSharedLibsDir(): File {
        val cwd = File(System.getProperty("user.dir", "."))

        val candidates = listOf(
            File(cwd, "macos/libs"),
            File(cwd, "libs"),
            File("../Resources/libs"),
            File("../lib/libs"),
        )

        return candidates.firstOrNull { it.isDirectory } ?: File(cwd, "libs")
    }

    /**
     * Load source instances from the extension JAR.
     */
    private fun loadSources(
        classLoader: URLClassLoader,
        metadata: ExtensionMetadata,
    ): List<eu.kanade.tachiyomi.source.Source> {
        val sourceClassNames = metadata.sourceClass
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sourceClassNames.isEmpty()) {
            throw IllegalStateException("No source classes defined for ${metadata.pkgName}")
        }

        return sourceClassNames.flatMap { className ->                try {
                    val fullClassName = if (className.startsWith(".")) {
                        metadata.pkgName + className
                    } else {
                        className
                    }

                    // Use false (don't initialize) to avoid resolving android.* class references
                    // that don't exist on JVM. The class will be initialized lazily when first used.
                    // Keep NoClassDefFoundError catch below as an additional safety net.
                    val clazz = Class.forName(fullClassName, false, classLoader)
                    val instance = clazz.getDeclaredConstructor().newInstance()

                    when (instance) {
                        is eu.kanade.tachiyomi.source.Source -> {
                            logger.info { "Loaded Source directly: $fullClassName" }
                            listOf(instance)
                        }
                        is eu.kanade.tachiyomi.source.SourceFactory -> {
                            logger.info { "Loaded SourceFactory: $fullClassName — creating sources..." }
                            instance.createSources()
                        }
                        is AnimeSource -> {
                            logger.warn { "Class $fullClassName implements AnimeSource but not Source. Wrapping via SourceAdapter." }
                            listOf(SourceAdapter(instance))
                        }
                        else -> {
                            // Try reflection-based wrapping for real extension JARs
                            val wrapped = wrapAsSource(instance)
                            if (wrapped != null) {
                                logger.info { "Wrapped ${instance.javaClass.name} via reflection as CatalogueSource" }
                                listOf(wrapped)
                            } else {
                                throw IllegalStateException(
                                    "Unknown source class type for $fullClassName: ${instance.javaClass.name}"
                                )
                            }
                        }
                    }
                } catch (e: NoClassDefFoundError) {
                    logger.warn { "Skipping $className — missing JVM dependency: ${e.message}" }
                    emptyList()
                } catch (e: Exception) {
                    logger.warn { "Skipping $className — instantiation failed: ${e.message}" }
                    emptyList()
                }
        }
    }

    /**
     * Compute SHA-256 hash of a file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Close the class loader for a specific package.
     */
    fun closeClassLoader(pkgName: String) {
        classLoaders.remove(pkgName)?.close()
    }

    /**
     * Close all tracked class loaders.
     */
    fun closeAll() {
        classLoaders.values.forEach { it.close() }
        classLoaders.clear()
    }

    private fun logAndNull(message: String): Nothing? {
        logger.warn { message }
        return null
    }

    /**
     * Adapter that wraps an AnimeSource as a Source for compatibility.
     */
    private class SourceAdapter(
        private val delegate: AnimeSource,
    ) : eu.kanade.tachiyomi.source.Source {
        override val id: Long get() = delegate.id
        override val name: String get() = delegate.name
        override val lang: String get() = delegate.lang

        override suspend fun getAnimeDetails(anime: eu.kanade.tachiyomi.animesource.model.SAnime) =
            delegate.getAnimeDetails(anime)

        override suspend fun getEpisodeList(anime: eu.kanade.tachiyomi.animesource.model.SAnime) =
            delegate.getEpisodeList(anime)

        override suspend fun getVideoList(episode: eu.kanade.tachiyomi.animesource.model.SEpisode) =
            delegate.getVideoList(episode)
    }
}
