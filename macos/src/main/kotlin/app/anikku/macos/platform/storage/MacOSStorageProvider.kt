package app.anikku.macos.platform.storage

import tachiyomi.core.common.storage.FolderProvider
import java.io.File

/**
 * macOS storage provider implementing FolderProvider.
 *
 * Base directory: ~/Library/Application Support/Anikku/
 * Subdirectories: downloads/, backups/, extensions/, logs/, covers/, data/
 */
class MacOSStorageProvider : FolderProvider {

    override fun directory(): File = baseDirectory

    override fun path(): String = baseDirectory.toURI().toString()

    val downloadsDirectory: File get() = File(baseDirectory, "downloads")
    val backupsDirectory: File get() = File(baseDirectory, "backups")
    val extensionsDirectory: File get() = File(baseDirectory, "extensions")
    val logsDirectory: File get() = File(baseDirectory, "logs")
    val coversDirectory: File get() = File(baseDirectory, "covers")
    val dataDirectory: File get() = File(baseDirectory, "data")

    fun ensureDirectories() {
        downloadsDirectory.mkdirs()
        backupsDirectory.mkdirs()
        extensionsDirectory.mkdirs()
        logsDirectory.mkdirs()
        coversDirectory.mkdirs()
        dataDirectory.mkdirs()
    }

    companion object {
        val baseDirectory: File by lazy {
            File(
                System.getProperty("user.home"),
                "Library${File.separator}Application Support${File.separator}Anikku",
            )
        }
    }
}
