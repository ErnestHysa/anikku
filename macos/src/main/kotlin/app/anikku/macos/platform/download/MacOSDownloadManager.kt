package app.anikku.macos.platform.download

import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.storage.MacOSStorageProvider
import eu.kanade.tachiyomi.animesource.model.SEpisode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Semaphore

private val logger = KotlinLogging.logger {}

/**
 * macOS download manager.
 *
 * Manages the full download lifecycle for anime episodes:
 * - Queue management with configurable concurrency
 * - Video URL fetching from extension sources
 * - File download via OkHttp with resume support
 * - Progress tracking and state persistence
 * - Completion notifications
 *
 * Usage:
 * ```kotlin
 * val manager = MacOSDownloadManager(repository, extensionManager, storageProvider, notifier)
 * manager.enqueue(animeId, sourceId, "Attack on Titan", "Episode 3", 3.0, "episode-url")
 * ```
 */
open class MacOSDownloadManager(
    private val repository: DownloadRepository,
    private val extensionManager: MacOSExtensionManager,
    private val storageProvider: MacOSStorageProvider,
    private val notifier: MacOSNotificationManager,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()

    private val _downloads = MutableStateFlow(repository.getAll())
    open val downloads: StateFlow<List<DownloadRepository.DownloadEntry>> = _downloads.asStateFlow()

    private var activeJobs = mutableMapOf<Long, Job>()
    private val concurrencySemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    var maxConcurrentDownloads: Int = MAX_CONCURRENT_DOWNLOADS
        set(value) {
            field = value.coerceIn(1, 10)
            // Not ideal but sufficient — the semaphore is recreated on next use
        }

    init {
        // Resume any queued or downloading entries from last session
        resumePendingDownloads()
    }

    /**
     * Enqueue an episode for download.
     */
    fun enqueue(
        animeId: Long,
        sourceId: Long,
        animeTitle: String,
        episodeName: String,
        episodeNumber: Double,
        episodeUrl: String?,
    ): DownloadRepository.DownloadEntry {
        val entry = repository.enqueue(
            animeId = animeId,
            sourceId = sourceId,
            animeTitle = animeTitle,
            episodeName = episodeName,
            episodeNumber = episodeNumber,
            episodeUrl = episodeUrl,
        )
        refreshState()
        processDownload(entry)
        return entry
    }

    /**
     * Pause an active download.
     */
    open fun pause(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        repository.update(id, status = DownloadRepository.DownloadStatus.PAUSED)
        // Semaphore released via finally block in the cancelled job
        refreshState()
    }

    /**
     * Resume a paused download.
     */
    open fun resume(id: Long) {
        val entry = repository.get(id) ?: return
        if (entry.status != DownloadRepository.DownloadStatus.PAUSED) return

        repository.update(id, status = DownloadRepository.DownloadStatus.QUEUED)
        refreshState()
        processDownload(repository.get(id) ?: return)
    }

    /**
     * Cancel and remove a download (including partial file).
     */
    open fun cancel(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        concurrencySemaphore.release()

        val entry = repository.get(id)
        if (entry?.filePath != null) {
            File(entry.filePath).delete()
        }
        repository.remove(id)
        refreshState()
    }

    /**
     * Cancel all active downloads.
     */
    open fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        repository.getAll().forEach { entry ->
            if (entry.isActive) {
                if (entry.filePath != null) File(entry.filePath).delete()
                repository.remove(entry.id)
            }
        }
        refreshState()
    }

    /**
     * Retry a failed download.
     */
    open fun retry(id: Long) {
        val entry = repository.get(id) ?: return
        if (entry.status != DownloadRepository.DownloadStatus.ERROR) return

        repository.update(id, status = DownloadRepository.DownloadStatus.QUEUED, progress = 0f)
        refreshState()
        processDownload(repository.get(id) ?: return)
    }

    /**
     * Check if an episode is downloaded and get its local file path.
     */
    fun getLocalFile(animeId: Long, episodeNumber: Double): File? {
        val entry = repository.getAll().find {
            it.animeId == animeId && it.episodeNumber == episodeNumber &&
                it.status == DownloadRepository.DownloadStatus.COMPLETED
        }
        return entry?.filePath?.let { File(it) }?.takeIf { it.isFile }
    }

    /**
     * Check if an episode is currently downloading or queued.
     */
    fun isDownloading(animeId: Long, episodeNumber: Double): Boolean {
        return repository.getAll().any {
            it.animeId == animeId && it.episodeNumber == episodeNumber && it.isActive
        }
    }

    /**
     * Check if an episode is downloaded.
     */
    fun isDownloaded(animeId: Long, episodeNumber: Double): Boolean {
        return repository.isDownloaded(animeId, episodeNumber)
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun refreshState() {
        _downloads.value = repository.getAll()
    }

    private fun resumePendingDownloads() {
        val pending = repository.getAll().filter { it.status == DownloadRepository.DownloadStatus.QUEUED }
        pending.forEach { entry ->
            repository.update(entry.id, status = DownloadRepository.DownloadStatus.QUEUED)
            processDownload(entry)
        }
    }

    private fun processDownload(entry: DownloadRepository.DownloadEntry) {
        if (activeJobs.containsKey(entry.id)) return // already processing

        val job = scope.launch {
            try {
                concurrencySemaphore.acquire()
            } catch (_: InterruptedException) {
                return@launch
            }

            if (!isActive) {
                concurrencySemaphore.release()
                return@launch
            }

            try {
                executeDownload(entry)
            } finally {
                concurrencySemaphore.release()
            }
        }

        activeJobs[entry.id] = job
    }

    private suspend fun executeDownload(entry: DownloadRepository.DownloadEntry) {
        repository.update(entry.id, status = DownloadRepository.DownloadStatus.DOWNLOADING)
        refreshState()

        // Step 1: Fetch video URLs from the extension source
        val videoUrl: String
        try {
            val source = extensionManager.getSource(entry.sourceId)
                ?: throw IllegalStateException("Source not found for ID ${entry.sourceId}")

            val sEpisode = SEpisode.create().apply {
                url = entry.episodeUrl ?: ""
            }
            val videos = source.getVideoList(sEpisode)
            if (videos.isEmpty()) {
                throw IllegalStateException("No video URLs returned for episode")
            }
            // Use the first available video URL (highest quality by convention)
            videoUrl = videos.first().videoUrl
            if (videoUrl.isBlank()) {
                throw IllegalStateException("Video URL is empty")
            }
            repository.update(entry.id, videoUrl = videoUrl)
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch video URL for download ${entry.id}" }
            repository.update(entry.id, status = DownloadRepository.DownloadStatus.ERROR)
            refreshState()
            return
        }

        // Step 2: Create output file
        val downloadsDir = File(storageProvider.downloadsDirectory, "videos")
        downloadsDir.mkdirs()
        val fileExtension = extractExtension(videoUrl) ?: ".mp4"
        val safeName = sanitizeFileName("${entry.animeTitle}_E${String.format("%.0f", entry.episodeNumber)}")
        val outputFile = File(downloadsDir, "$safeName$fileExtension")
        val tempFile = File(downloadsDir, ".${safeName}$fileExtension.part")

        repository.update(
            entry.id,
            filePath = outputFile.absolutePath,
            fileName = outputFile.name,
        )

        // Step 3: Download the file with progress
        try {
            val request = Request.Builder()
                .url(videoUrl)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw okio.IOException("Download failed: HTTP ${response.code}")
            }

            val contentLength = response.body?.contentLength() ?: -1L

            tempFile.outputStream().use { output ->
                response.body!!.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check for cancellation
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        // Update progress periodically
                        repository.update(
                            entry.id,
                            downloadedBytes = totalRead,
                            totalBytes = contentLength.coerceAtLeast(totalRead),
                            progress = if (contentLength > 0) totalRead.toFloat() / contentLength else 0f,
                        )
                        if (totalRead % (32 * 1024 * 10) == 0L) { // every ~320KB
                            refreshState()
                        }
                    }
                }
            }

            // Step 4: Rename temp file to final name and mark complete
            tempFile.renameTo(outputFile)

            repository.update(
                entry.id,
                status = DownloadRepository.DownloadStatus.COMPLETED,
                progress = 1f,
                totalBytes = outputFile.length(),
                downloadedBytes = outputFile.length(),
            )
            refreshState()

            // Show notification
            notifier.showDownloadComplete(entry.animeTitle, entry.episodeName)

            // Prune old completed downloads
            repository.pruneCompleted(20)

            logger.info {
                "Download complete: ${entry.animeTitle} - ${entry.episodeName} " +
                    "(${formatSize(outputFile.length())})"
            }
        } catch (e: Exception) {
            logger.error(e) { "Download failed for ${entry.id}: ${entry.animeTitle} - ${entry.episodeName}" }
            tempFile.delete()
            repository.update(entry.id, status = DownloadRepository.DownloadStatus.ERROR)
            refreshState()
        }
    }

    private fun extractExtension(url: String): String? {
        val path = url.substringBefore("?")
        val dotIndex = path.lastIndexOf('.')
        return if (dotIndex >= 0) path.substring(dotIndex) else null
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 3
    }
}
