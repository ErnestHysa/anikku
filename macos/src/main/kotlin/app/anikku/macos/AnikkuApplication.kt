package app.anikku.macos

import app.anikku.macos.platform.BackgroundTaskScheduler
import app.anikku.macos.platform.database.MacOSDatabaseDriver
import app.anikku.macos.platform.logging.MacOSLogger
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.storage.MacOSStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.dsl.module
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.storage.FolderProvider
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

        // 5. Start Koin with macOS-specific modules
        startKoin {
            modules(macOSModule(this@AnikkuApplication))
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
    }

    companion object {
        /**
         * Creates a Koin module that registers the platform-specific dependencies
         * from the application instance.
         */
        fun macOSModule(app: AnikkuApplication) = module {
            single<MacOSStorageProvider> { app.storageProvider }
            single<FolderProvider> { app.storageProvider }
            single<MacOSPreferenceStore> { app.preferenceStore }
            single<PreferenceStore> { app.preferenceStore }
            single<MacOSDatabaseDriver> { app.databaseDriver }
            single<BackgroundTaskScheduler> { app.backgroundScheduler }
        }
    }
}
