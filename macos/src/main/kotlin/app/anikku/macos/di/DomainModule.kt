package app.anikku.macos.di

import app.anikku.macos.AnikkuApplication
import app.anikku.macos.platform.data.MacOSCustomAnimeRepository
import app.anikku.macos.platform.storage.MacOSStorageManager
import org.koin.dsl.module

/**
 * Domain & Data layer Koin module.
 *
 * Registers business logic and data repository implementations:
 * - Storage manager: file system operations (backups, downloads, mpv config)
 * - Custom anime repository: user-defined anime metadata edits
 *
 * Future Phase 5+ additions (when domain/data modules are wired to macOS build):
 * - AnimeRepository, CategoryRepository (data layer implementations)
 * - GetLibraryAnime, GetCategories, GetHistory (domain interactors)
 * - LibraryPreferences, SourceManager (domain services)
 */
fun domainModule(app: AnikkuApplication) = module {

    // Phase 2: Storage & Data
    single<MacOSStorageManager> { app.storageManager }
    single<MacOSCustomAnimeRepository> { app.customAnimeRepository }
}
