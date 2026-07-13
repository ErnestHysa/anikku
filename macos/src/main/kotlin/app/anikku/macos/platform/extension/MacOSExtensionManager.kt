package app.anikku.macos.platform.extension

import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.storage.MacOSStorageProvider
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import okio.IOException
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.days

private val logger = KotlinLogging.logger {}

/**
 * macOS extension manager.
 *
 * Manages the lifecycle of anime extensions as JAR files:
 * - Scanning installed extensions from the extensions directory
 * - Fetching available extensions from remote repositories
 * - Downloading and installing new or updated extensions
 * - Removing extensions
 * - Trust management (SHA-256 signature verification)
 *
 * Replaces the Android ExtensionManager which uses PackageManager + APK installs.
 */
class MacOSExtensionManager(
    private val storageProvider: MacOSStorageProvider,
    private val networkHelper: MacOSNetworkHelper,
) : AutoCloseable {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val extensionsDir: File get() = storageProvider.extensionsDirectory
    private val trustDir: File get() = File(storageProvider.dataDirectory, "trust")
    private val trustFile get() = File(trustDir, "trusted_extensions.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Timestamp of last extension repo check (for rate limiting) */
    private var lastExtensionCheck: Long = 0

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val installedExtensionsMapFlow = MutableStateFlow(emptyMap<String, Extension.Installed>())
    val installedExtensionsFlow: StateFlow<List<Extension.Installed>> = installedExtensionsMapFlow
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val availableExtensionsMapFlow = MutableStateFlow(emptyMap<String, Extension.Available>())
    val availableExtensionsFlow: StateFlow<List<Extension.Available>> = availableExtensionsMapFlow
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val untrustedExtensionsMapFlow = MutableStateFlow(emptyMap<String, Extension.Untrusted>())
    val untrustedExtensionsFlow: StateFlow<List<Extension.Untrusted>> = untrustedExtensionsMapFlow
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private var trustStore: MutableMap<String, MutableList<MacOSExtensionLoader.TrustEntry>> = mutableMapOf()

    /** Whether NSFW extensions should be loaded */
    var loadNsfwSource: Boolean = false

    init {
        ensureDirectories()
        loadTrustStore()
        initExtensions()
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    private fun ensureDirectories() {
        extensionsDir.mkdirs()
        trustDir.mkdirs()
    }

    /**
     * Scan the extensions directory and load all installed extensions.
     */
    private fun initExtensions() {
        val results = MacOSExtensionLoader.loadExtensions(
            extensionsDir = extensionsDir,
            trustStore = trustStore,
            loadNsfw = loadNsfwSource,
        )

        installedExtensionsMapFlow.value = results
            .filterIsInstance<LoadResult.Success>()
            .associate { it.extension.pkgName to it.extension }

        untrustedExtensionsMapFlow.value = results
            .filterIsInstance<LoadResult.Untrusted>()
            .associate { it.extension.pkgName to it.extension }

        _isInitialized.value = true
        logger.info {
            "Loaded ${installedExtensionsMapFlow.value.size} extensions, " +
                "${untrustedExtensionsMapFlow.value.size} untrusted"
        }
    }

    /**
     * Reload a single extension after install/update (avoids full rescan).
     */
    fun reloadExtension(pkgName: String) {
        val jarFile = File(extensionsDir, "$pkgName.jar")
        if (!jarFile.isFile) {
            installedExtensionsMapFlow.value -= pkgName
            untrustedExtensionsMapFlow.value -= pkgName
            MacOSExtensionLoader.closeClassLoader(pkgName)
            return
        }

        val result = MacOSExtensionLoader.loadExtension(
            jarFile = jarFile,
            trustStore = trustStore,
            loadNsfw = loadNsfwSource,
        )

        when (result) {
            is LoadResult.Success -> {
                installedExtensionsMapFlow.value += pkgName to result.extension
                untrustedExtensionsMapFlow.value -= pkgName
            }
            is LoadResult.Untrusted -> {
                installedExtensionsMapFlow.value -= pkgName
                untrustedExtensionsMapFlow.value += pkgName to result.extension
            }
            is LoadResult.Error -> {
                installedExtensionsMapFlow.value -= pkgName
                untrustedExtensionsMapFlow.value -= pkgName
            }
        }
    }

    // -------------------------------------------------------------------------
    // Available extensions (repository)
    // -------------------------------------------------------------------------

    /**
     * Fetch available extensions from a repository URL.
     * Rate-limited to once per day per URL (matches Android behavior).
     *
     * @param repoBaseUrl Base URL of the extension repository
     * @param force Bypass rate limiting
     * @return List of available extensions
     */
    suspend fun findAvailableExtensions(
        repoBaseUrl: String,
        force: Boolean = false,
    ): List<Extension.Available> {
        // Rate limit: once per day (matches Android ExtensionApi behavior)
        val now = Instant.now().toEpochMilli()
        if (!force && now < lastExtensionCheck + 1.days.inWholeMilliseconds) {
            logger.debug { "Rate limited extension check. Next allowed after ${lastExtensionCheck + 1.days.inWholeMilliseconds}" }
            return availableExtensionsFlow.value
        }

        return try {
            val indexUrl = "$repoBaseUrl/index.min.json"
            val request = Request.Builder()
                .url(indexUrl)
                .get()
                .build()

            val response = networkHelper.client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val extList: List<ExtensionJsonObject> = json.decodeFromString(body)

            val extensions = extList
                .filter {
                    val libVersion = it.extractLibVersion()
                    libVersion >= MacOSExtensionLoader.LIB_VERSION_MIN &&
                        libVersion <= MacOSExtensionLoader.LIB_VERSION_MAX
                }
                .map { it.toExtension(repoBaseUrl) }

            availableExtensionsMapFlow.value = extensions.associateBy { it.pkgName }
            updateInstalledStatuses(extensions)
            lastExtensionCheck = now

            extensions
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    private fun updateInstalledStatuses(availableExtensions: List<Extension.Available>) {
        val installed = installedExtensionsMapFlow.value.toMutableMap()
        var changed = false

        for ((pkgName, extension) in installed) {
            val availableExt = availableExtensions.find { it.pkgName == pkgName }
            if (availableExt == null && !extension.isObsolete) {
                installed[pkgName] = extension.copy(isObsolete = true)
                changed = true
            } else if (availableExt != null) {
                val hasUpdate = availableExt.versionCode > extension.versionCode ||
                    availableExt.libVersion > extension.libVersion
                if (extension.hasUpdate != hasUpdate || extension.repoUrl != availableExt.repoUrl) {
                    installed[pkgName] = extension.copy(
                        hasUpdate = hasUpdate,
                        repoUrl = availableExt.repoUrl,
                    )
                    changed = true
                }
            }
        }

        if (changed) {
            installedExtensionsMapFlow.value = installed
        }
    }

    // -------------------------------------------------------------------------
    // Install / Update / Remove
    // -------------------------------------------------------------------------

    /**
     * Download and install an extension.
     *
     * The repo serves Android APK files. On macOS these must be converted to
     * JVM JARs via jadx decompilation + javac recompilation. If jadx is not
     * installed, the APK is saved with a .apk extension and will be converted
     * on the next app startup (via [MacOSExtensionLoader.loadExtensions]).
     */
    suspend fun installExtension(
        extension: Extension.Available,
        onProgress: ((InstallStep) -> Unit)? = null,
    ) {
        val apkUrl = "${extension.repoUrl}/apk/${extension.apkName}"
        val apkTmpFile = File(extensionsDir, "${extension.pkgName}.apk.tmp")
        val finalJar = File(extensionsDir, "${extension.pkgName}.jar")
        val apkFile = File(extensionsDir, "${extension.pkgName}.apk")

        try {
            onProgress?.invoke(InstallStep.Downloading(0f))

            // Remove any previous downloads or JARs for this package
            apkFile.delete()
            apkTmpFile.delete()
            finalJar.delete()

            val request = Request.Builder()
                .url(apkUrl)
                .get()
                .build()

            val response = networkHelper.client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Download failed: ${response.code} ${response.message}")
            }

            // Download to temp file
            response.body?.byteStream()?.use { input ->
                apkTmpFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    val contentLength = response.body?.contentLength() ?: -1L
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress?.invoke(
                                InstallStep.Downloading(totalRead.toFloat() / contentLength)
                            )
                        }
                    }
                }
            }

            onProgress?.invoke(InstallStep.Installing)

            // Rename temp to .apk/.tmp after successful download
            apkTmpFile.renameTo(apkFile)

            // Determine whether the downloaded file is a pre-converted JAR or an APK
            val isPreConvertedJar = extension.apkName.endsWith(".jar", ignoreCase = true)

            if (isPreConvertedJar) {
                // Pre-converted JAR from repo — rename directly (atomic on same filesystem)
                logger.info { "Extension is already a JAR: ${extension.pkgName}" }
                apkFile.renameTo(finalJar)
                logger.info { "Installed JAR directly: ${extension.pkgName} (${finalJar.length()} bytes)" }
            } else if (DexClassLoader.isAvailable()) {
                // APK — needs jadx conversion
                logger.info { "Converting APK to JAR: ${extension.pkgName}" }
                val sourceApiJar = findSourceApiJar()
                val commonJvmJar = findCommonJvmJar()
                val success = DexClassLoader.convertToJar(apkFile, finalJar, sourceApiJar, commonJvmJar)

                if (success && finalJar.isFile && finalJar.length() > 0) {
                    apkFile.delete()
                    logger.info { "Converted ${extension.pkgName} to JAR (${finalJar.length()} bytes)" }
                } else {
                    // Conversion failed — clean up both APK and broken JAR
                    apkFile.delete()
                    finalJar.delete()
                    logger.warn { "APK conversion failed for ${extension.pkgName}. User should install jadx and retry." }
                    throw IOException("Conversion failed. Ensure jadx is installed (brew install jadx) and retry.")
                }
            } else {
                // No jadx available — keep APK for batch conversion on startup
                logger.info { "jadx not available — ${extension.pkgName}.apk saved for batch conversion on restart" }
            }

            onProgress?.invoke(InstallStep.Complete)

            // Load the newly installed extension
            reloadExtension(extension.pkgName)
            logger.info { "Installed extension: ${extension.pkgName}" }
        } catch (e: Exception) {
            apkTmpFile.delete()
            logger.error(e) { "Failed to install extension: ${extension.pkgName}" }
            onProgress?.invoke(InstallStep.Error(e.message ?: "Unknown error"))
            // Don't throw — let the UI show the error message gracefully
        }
    }

    /**
     * Locate [source-api-jvm.jar] needed for APK-to-JAR conversion.
     *
     * Search order:
     * 1. Bundled in the .app Resources (when running from DMG)
     * 2. Project libs/ directory (during development via gradlew run)
     * 3. Current working directory
     */
    private fun findSourceApiJar(): File? {
        val cwd = File(System.getProperty("user.dir", "."))
        val candidates = listOf(
            // Bundled in .app/Contents/Resources/libs/
            File("../Resources/libs/source-api-jvm.jar"),
            File("../lib/libs/source-api-jvm.jar"),
            // Project root (development)
            File(cwd, "macos/libs/source-api-jvm.jar"),
            File(cwd, "libs/source-api-jvm.jar"),
            // Current directory
            File("macos/libs/source-api-jvm.jar"),
            File("libs/source-api-jvm.jar"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    /**
     * Locate [common-jvm.jar] needed for APK-to-JAR conversion.
     */
    private fun findCommonJvmJar(): File? {
        val cwd = File(System.getProperty("user.dir", "."))
        val candidates = listOf(
            File("../Resources/libs/common-jvm.jar"),
            File("../lib/libs/common-jvm.jar"),
            File(cwd, "macos/libs/common-jvm.jar"),
            File(cwd, "libs/common-jvm.jar"),
            File("macos/libs/common-jvm.jar"),
            File("libs/common-jvm.jar"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    /**
     * Update an installed extension.
     */
    suspend fun updateExtension(
        extension: Extension.Installed,
        onProgress: ((InstallStep) -> Unit)? = null,
    ) {
        val availableExt = availableExtensionsMapFlow.value[extension.pkgName]
            ?: throw IllegalStateException("No available update for ${extension.pkgName}")
        installExtension(availableExt, onProgress)
    }

    /**
     * Remove (uninstall) an extension.
     */
    fun removeExtension(extension: Extension) {
        MacOSExtensionLoader.closeClassLoader(extension.pkgName)

        val jarFile = File(extensionsDir, "${extension.pkgName}.jar")
        if (jarFile.exists()) {
            jarFile.delete()
            logger.info { "Removed extension: ${extension.pkgName}" }
        }

        installedExtensionsMapFlow.value -= extension.pkgName
        untrustedExtensionsMapFlow.value -= extension.pkgName
    }

    // -------------------------------------------------------------------------
    // Trust management
    // -------------------------------------------------------------------------

    private fun loadTrustStore() {
        if (trustFile.isFile) {
            try {
                val content = trustFile.readText()
                val entries: List<MacOSExtensionLoader.TrustEntry> = json.decodeFromString(content)
                trustStore = entries
                    .groupByTo(mutableMapOf()) { it.pkgName }
                    .mapValues { (_, v) -> v.toMutableList() }
                    .toMutableMap()
                logger.info { "Loaded trust store: ${trustStore.size} packages trusted" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load trust store" }
            }
        }

        // Always scan for new JARs not yet in the trust store and auto-trust them.
        // This handles fresh installs (no trust file) AND new JARs added after
        // a batch rebuild without requiring the user to manually trust each one.
        autoTrustAllJars()
    }

    /**
     * Auto-trust JAR files in the extensions directory that are not already trusted.
     *
     * Called on every startup to handle:
     * - Fresh installs (no trust store exists yet)
     * - New JARs added after batch rebuilds
     * - Manually placed extension JARs
     *
     * Only trusts packages NOT already in the trust store — previously trusted
     * packages with updated hashes still require manual re-trust for security.
     * Users can always revoke trust for individual extensions via the UI.
     */
    private fun autoTrustAllJars() {
        val jars = extensionsDir.listFiles()
            ?.filter { it.extension == "jar" }
            ?: emptyList()

        if (jars.isEmpty()) return

        logger.info { "🔑 Auto-trust scan: checking ${jars.size} JAR(s) against trust store (${trustStore.size} packages known)..." }

        var newCount = 0
        var skippedCount = 0
        for (jar in jars) {
            val metadata = MacOSExtensionLoader.readMetadata(jar)
            if (metadata == null) {
                logger.debug { "  No metadata for ${jar.name} — skipping" }
                continue
            }

            val hash = MacOSExtensionLoader.computeSha256(jar)

            // Skip only if already trusted with this exact hash.
            // Checking pkgName alone is insufficient — batch rebuilds produce
            // new hashes for the same package, and the old hash won't match.
            val alreadyTrusted = trustStore[metadata.pkgName]
                ?.any { it.signatureHash == hash } == true
            if (alreadyTrusted) {
                skippedCount++
                continue
            }

            val entry = MacOSExtensionLoader.TrustEntry(
                pkgName = metadata.pkgName,
                versionCode = metadata.versionCode,
                signatureHash = hash,
            )
            trustStore.getOrPut(metadata.pkgName) { mutableListOf() }.add(entry)
            logger.info { "  ✅ Auto-trusted: ${metadata.pkgName} (hash: ${hash.take(12)}...)" }
            newCount++
        }

        logger.info { "🔑 Auto-trust done: $newCount new trusted, $skippedCount already trusted, ${trustStore.size} total packages" }

        if (newCount > 0) {
            saveTrustStore()
            logger.info { "💾 Trust store saved (${trustStore.size} packages)" }
        }
    }

    private fun saveTrustStore() {
        try {
            val entries = trustStore.values.flatten()
            trustFile.writeText(
                json.encodeToString(
                    ListSerializer(MacOSExtensionLoader.TrustEntry.serializer()),
                    entries,
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to save trust store" }
        }
    }

    /**
     * Trust an untrusted extension.
     */
    fun trustExtension(extension: Extension.Untrusted) {
        val entry = MacOSExtensionLoader.TrustEntry(
            pkgName = extension.pkgName,
            versionCode = extension.versionCode,
            signatureHash = extension.signatureHash,
        )

        trustStore.getOrPut(extension.pkgName) { mutableListOf() }.add(entry)
        saveTrustStore()

        // Remove from untrusted and reload
        untrustedExtensionsMapFlow.value -= extension.pkgName
        reloadExtension(extension.pkgName)

        logger.info { "Trusted extension: ${extension.pkgName}" }
    }

    /**
     * Check if a package is trusted.
     */
    fun isTrusted(pkgName: String, signatureHash: String): Boolean {
        return trustStore[pkgName]?.any { it.signatureHash == signatureHash } == true
    }

    /**
     * Revoke trust for a package.
     */
    fun revokeTrust(pkgName: String) {
        trustStore.remove(pkgName)
        saveTrustStore()
        reloadExtension(pkgName)
    }

    // -------------------------------------------------------------------------
    // Source lookup
    // -------------------------------------------------------------------------

    /**
     * Look up a source by its ID across all installed extensions.
     *
     * @param sourceId The source ID to find.
     * @return The source if found, null otherwise.
     */
    fun getSource(sourceId: Long): eu.kanade.tachiyomi.source.Source? {
        return installedExtensionsMapFlow.value.values
            .flatMap { it.sources }
            .filterIsInstance<eu.kanade.tachiyomi.source.Source>()
            .find { it.id == sourceId }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Shut down the extension manager. Cancels coroutine scope and closes all class loaders.
     */
    override fun close() {
        scope.cancel()
        MacOSExtensionLoader.closeAll()
        logger.info { "Extension manager shut down" }
    }
}

// ---------------------------------------------------------------------------
// Extension JSON models (matches Android ExtensionApi format)
// ---------------------------------------------------------------------------

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int = 0,
    val torrent: Int = 0,
    val sources: List<ExtensionSourceJsonObject>? = null,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private fun ExtensionJsonObject.extractLibVersion(): Double {
    return version.substringBeforeLast('.').toDouble()
}

private fun ExtensionJsonObject.toExtension(repoUrl: String): Extension.Available {
    return Extension.Available(
        name = name.substringAfter("Aniyomi: "),
        pkgName = pkg,
        versionName = version,
        versionCode = code,
        libVersion = extractLibVersion(),
        lang = lang,
        isNsfw = nsfw == 1,
        isTorrent = torrent == 1,
        sources = sources?.map { source ->
            Extension.Available.AnimeSource(
                id = source.id,
                lang = source.lang,
                name = source.name,
                baseUrl = source.baseUrl,
            )
        }.orEmpty(),
        apkName = apk,
        iconUrl = "$repoUrl/icon/$pkg.png",
        repoUrl = repoUrl,
    )
}
