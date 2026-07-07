package tachiyomi.core.common.storage

import java.io.File

/**
 * Local stub interface for FolderProvider.
 * In Phase 2, delete this file and use the real interface from the shared core/common module.
 */
interface FolderProvider {
    fun directory(): File
    fun path(): String
}
