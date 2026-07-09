package app.anikku.macos

import app.anikku.macos.di.appModule
import app.anikku.macos.di.domainModule
import app.anikku.macos.di.platformModule
import app.anikku.macos.platform.BackgroundTaskScheduler
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.MacOSCustomAnimeRepository
import app.anikku.macos.platform.database.MacOSDatabaseDriver
import app.anikku.macos.platform.discord.DiscordRPC
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.logging.CrashReporter
import app.anikku.macos.platform.logging.MacOSLogger
import app.anikku.macos.platform.network.MacOSCookieJar
import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSBiometricAuth
import app.anikku.macos.platform.storage.MacOSStorageManager
import app.anikku.macos.platform.storage.MacOSStorageProvider
import app.anikku.macos.platform.update.AppUpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import java.io.File

/**
 * macOS desktop application initialization.
 * Replaces the Android App.kt Application class lifecycle.
 *
 * Responsibilities:
 * - Initialize logging
 * - Set up Koin dependency injection
 * - Configure storage directories
 * - Initialize database
 * - Set up background task scheduler
 *
 * This is called once at application startup from AnikkuApp.kt.
 */
class AnikkuApplication {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val storageProvider = MacOSStorageProvider()
    val preferenceStore: MacOSPreferenceStore
    val backgroundScheduler = BackgroundTaskScheduler(applicationScope)
    val databaseDriver: MacOSDatabaseDriver

    // Phase 2: Storage & Data
    val storageManager: MacOSStorageManager
    val customAnimeRepository: MacOSCustomAnimeRepository
    val libraryRepository: LibraryRepository
    val historyRepository: HistoryRepository

    // Phase 3: Networking
    val networkHelper: MacOSNetworkHelper
    val cookieJar: MacOSCookieJar

    // Phase 3: Extension system
    val extensionManager: MacOSExtensionManager

    // Phase 7: Advanced Features
    val discordRPC: DiscordRPC
    val notificationManager: MacOSNotificationManager
    val biometricAuth: MacOSBiometricAuth
    val appUpdateChecker: AppUpdateChecker

    init {
        // 1. Ensure storage directories exist
        storageProvider.ensureDirectories()

        // 2. Initialize logging
        MacOSLogger.initialize(storageProvider)

        // 3. Deploy bundled extensions on first launch (Phase 3.3)
        deployBundledExtensions()

        // 4. Initialize preferences (JSON file-backed)
        val prefsFile = File(storageProvider.dataDirectory, "preferences.json")
        preferenceStore = MacOSPreferenceStore(prefsFile)

        // 5. Initialize database driver
        databaseDriver = MacOSDatabaseDriver(storageProvider)

        // 5. Initialize networking (Phase 3.1-3.2)
        networkHelper = MacOSNetworkHelper(storageProvider)
        cookieJar = networkHelper.cookieJar

        // 6. Initialize extension system (Phase 3.3)
        extensionManager = MacOSExtensionManager(storageProvider, networkHelper)

        // 7. Initialize storage manager (Phase 2.1)
        storageManager = MacOSStorageManager(storageProvider)

        // 8. Initialize custom anime repo (Phase 2.2)
        customAnimeRepository = MacOSCustomAnimeRepository(storageProvider.dataDirectory)

        // 8b. Initialize library and history repos
        libraryRepository = LibraryRepository(storageProvider.dataDirectory)
        historyRepository = HistoryRepository(storageProvider.dataDirectory)

        // 9. Initialize Phase 7: Advanced Features
        // 9a. Discord Rich Presence
        discordRPC = DiscordRPC(applicationScope)

        // 9b. macOS Notifications
        notificationManager = MacOSNotificationManager()
        notificationManager.initialize()

        // 9c. Biometric Authentication (Touch ID + PIN fallback)
        biometricAuth = MacOSBiometricAuth()

        // 9d. App Update Checker (GitHub API)
        appUpdateChecker = AppUpdateChecker(
            currentVersion = "1.0.0",
            repoOwner = "komikku-app",
            repoName = "anikku",
        )

        // 9e. Crash Reporting
        CrashReporter.initialize(
            storageProvider = storageProvider,
            version = "1.0.0",
        )

        // 10. Start Koin with modular dependency injection
        startKoin {
            modules(
                platformModule(this@AnikkuApplication),
                domainModule(this@AnikkuApplication),
                appModule(this@AnikkuApplication),
            )
        }

        MacOSLogger.getLogger<AnikkuApplication>()
            .info("Anikku macOS application initialized")
        CrashReporter.logEvent("App initialization complete")
    }

    /**
     * Deploy bundled extension JARs from the .app bundle to the user's extensions directory.
     *
     * On first launch (or any launch where the extensions directory is empty),
     * this copies pre-converted extension JARs from the application bundle's
     * Resources/libs/ directory so users have working extensions immediately.
     */
    private fun deployBundledExtensions() {
        val bundledLibsDir = File("../Resources/libs")
        if (!bundledLibsDir.isDirectory) return

        val extensionsDir = storageProvider.extensionsDirectory
        val jarFiles = bundledLibsDir.listFiles()
            ?.filter { it.extension == "jar" && it.name.contains("tachiyomi") }
            ?: emptyList()

        if (jarFiles.isEmpty()) return

        // Copy each bundled JAR that hasn't been installed yet
        var deployed = 0
        for (jarFile in jarFiles) {
            val targetFile = File(extensionsDir, jarFile.name)
            if (!targetFile.exists()) {
                jarFile.copyTo(targetFile, overwrite = false)
                deployed++
            }
        }

        if (deployed > 0) {
            MacOSLogger.getLogger<AnikkuApplication>()
                .info("Deployed $deployed bundled extension(s) to ${extensionsDir.absolutePath}")
        }
    }

    /**
     * Called when the main window gains focus.
     * Equivalent to onStart() in Android lifecycle.
     * Triggers Discord RPC reconnection and sync operations.
     */
    fun onAppFocused() {
        // Phase 7.3: Discord Rich Presence — connect on app focus
        if (discordRPC.isDiscordInstalled) {
            discordRPC.start()
        }
    }

    /**
     * Called when the main window loses focus.
     * Equivalent to onStop() in Android lifecycle.
     * Pauses Discord RPC and saves sync state.
     */
    fun onAppBlurred() {
        // Phase 7.3: Discord Rich Presence — disconnect on blur
        if (discordRPC.isConnected) {
            discordRPC.clearPresence()
        }
    }

    /**
     * Called when the application is shutting down.
     * Cleans up all Phase 7 services.
     */
    fun onShutdown() {
        backgroundScheduler.cancelAll()
        extensionManager.close()

        // Phase 7.3: Discord RPC
        discordRPC.stop()

        // Phase 7.6: Notifications
        notificationManager.shutdown()

        CrashReporter.logEvent("App shutdown")
    }

}
