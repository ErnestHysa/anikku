package app.anikku.macos

import app.anikku.macos.di.appModule
import app.anikku.macos.di.domainModule
import app.anikku.macos.di.platformModule
import app.anikku.macos.platform.BackgroundTaskScheduler
import app.anikku.macos.platform.data.MacOSCustomAnimeRepository
import app.anikku.macos.platform.database.MacOSDatabaseDriver
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.logging.MacOSLogger
import app.anikku.macos.platform.network.MacOSCookieJar
import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.storage.MacOSStorageManager
import app.anikku.macos.platform.storage.MacOSStorageProvider
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

    // Phase 3: Networking
    val networkHelper: MacOSNetworkHelper
    val cookieJar: MacOSCookieJar

    // Phase 3: Extension system
    val extensionManager: MacOSExtensionManager

    init {
        // 1. Ensure storage directories exist
        storageProvider.ensureDirectories()

        // 2. Initialize logging
        MacOSLogger.initialize(storageProvider)

        // 3. Initialize preferences (JSON file-backed)
        val prefsFile = File(storageProvider.dataDirectory, "preferences.json")
        preferenceStore = MacOSPreferenceStore(prefsFile)

        // 4. Initialize database driver
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

        // 9. Start Koin with modular dependency injection
        startKoin {
            modules(
                platformModule(this@AnikkuApplication),
                domainModule(this@AnikkuApplication),
                appModule(this@AnikkuApplication),
            )
        }

        MacOSLogger.getLogger<AnikkuApplication>()
            .info("Anikku macOS application initialized")
    }

    /**
     * Called when the main window gains focus.
     * Equivalent to onStart() in Android lifecycle.
     */
    fun onAppFocused() {
        // Future: sync triggers, Discord RPC start
    }

    /**
     * Called when the main window loses focus.
     * Equivalent to onStop() in Android lifecycle.
     */
    fun onAppBlurred() {
        // Future: sync triggers, Discord RPC stop
    }

    /**
     * Called when the application is shutting down.
     */
    fun onShutdown() {
        backgroundScheduler.cancelAll()
        extensionManager.close()
    }

}
