package app.anikku.macos.di

import app.anikku.macos.AnikkuApplication
import app.anikku.macos.platform.BackgroundTaskScheduler
import app.anikku.macos.platform.database.MacOSDatabaseDriver
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.network.MacOSCookieJar
import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.storage.MacOSStorageProvider
import org.koin.dsl.module
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.storage.FolderProvider

/**
 * Platform-specific Koin module.
 *
 * Registers macOS platform implementations that replace Android APIs:
 * - Storage: file-based storage provider
 * - Preferences: JSON file-backed preference store
 * - Database: JDBC SQLDelight driver
 * - Logging: SLF4J + Logback
 * - Networking: OkHttp client, cookie jar, DoH providers
 * - Extensions: JAR-based URLClassLoader extension loading
 * - Background tasks: coroutine-based scheduler
 */
fun platformModule(app: AnikkuApplication) = module {

    // Phase 1: Core infrastructure
    single<MacOSStorageProvider> { app.storageProvider }
    single<FolderProvider> { app.storageProvider }
    single<MacOSPreferenceStore> { app.preferenceStore }
    single<PreferenceStore> { app.preferenceStore }
    single<MacOSDatabaseDriver> { app.databaseDriver }
    single<BackgroundTaskScheduler> { app.backgroundScheduler }

    // Phase 3: Networking
    single<MacOSNetworkHelper> { app.networkHelper }
    single<MacOSCookieJar> { app.cookieJar }

    // Phase 3: Extension system
    single<MacOSExtensionManager> { app.extensionManager }
}
