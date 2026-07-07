package app.anikku.macos.platform.database

import app.anikku.macos.platform.storage.MacOSStorageProvider
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Provides a JDBC-based SQLDelight SqlDriver for macOS desktop.
 * Database file: ~/Library/Application Support/Anikku/data/anime.db
 *
 * TODO Phase 2: Replace placeholder table creation with actual Database.Schema
 * from the shared data module (version ~129 with all migrations).
 */
class MacOSDatabaseDriver(
    private val storageProvider: MacOSStorageProvider,
) {

    fun createDriver(): SqlDriver {
        val dbDir = storageProvider.dataDirectory
        dbDir.mkdirs()

        val dbFile = File(dbDir, "anime.db")
        val dbPath = dbFile.absolutePath

        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")

        // Placeholder: actual schema created by Database.Schema in Phase 2
        driver.execute(null, "CREATE TABLE IF NOT EXISTS _placeholder (id INTEGER PRIMARY KEY)", 0)

        // Enable WAL mode and foreign keys
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)
        driver.execute(null, "PRAGMA synchronous = NORMAL", 0)

        return driver
    }
}
