package tachiyomi.core.common.storage

import java.io.File

/**
 * Local stub interface for FolderProvider.
 * Matches the real interface from core/common.
 * Used until Phase 2 fully integrates shared modules.
 */
interface FolderProvider {
    fun directory(): File
    fun path(): String
}
