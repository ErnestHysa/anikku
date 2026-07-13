package app.anikku.macos.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.player.MPVLib
import app.anikku.macos.player.MPVSoftwareRenderer
import app.anikku.macos.player.MPVVideoSurface
import app.anikku.macos.player.PlaybackState
import app.anikku.macos.player.PlayerViewModel
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.download.MacOSDownloadManager
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.platform.media.MacOSHttpServer
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.OfflineBadge
import app.anikku.macos.ui.components.OfflineCheckmarkAnimation
import app.anikku.macos.ui.components.PlaybackStateBadge
import app.anikku.macos.ui.components.VideoQualityBadge
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.models.EpisodeModel
import app.anikku.macos.ui.screens.models.toEpisodeModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Player screen — Phase 6: mpv Integration + Phase 7: Offline playback.
 *
 * Full-screen video player with:
 * - MPV video surface (via JNA libmpv bindings)
 * - Auto-hiding controls overlay (top bar + bottom controls)
 * - Seek bar with elapsed / total time (from mpv position tracking)
 * - Play / pause, skip forward / backward
 * - Previous / next episode navigation
 * - Volume control
 * - Screenshot support
 * - Keyboard shortcuts (space, arrows, +/- for volume)
 * - **Offline playback**: if the episode is downloaded, serves it from
 *   local storage via [MacOSHttpServer] instead of fetching from source.
 *
 * When [sourceId] and [extensionManager] are provided, fetches video URLs
 * from the extension source API and loads them into mpv for real playback.
 * If [downloadManager] is provided, checks for local downloads first.
 * Falls back to mock simulation when source is not available.
 *
 * @param animeId     The anime these episodes belong to.
 * @param episodeId   The episode to start playing.
 * @param sourceId    Source ID for extension API lookup (optional).
 * @param episodeUrl  The episode URL on the source (required for source API).
 * @param extensionManager Extension manager for source lookup (optional).
 * @param downloadManager Download manager for offline playback (optional).
 */
data class PlayerScreen(
    val animeId: Long,
    val episodeId: Long,
    val sourceId: Long? = null,
    val episodeUrl: String? = null,
    val extensionManager: MacOSExtensionManager? = null,
    val downloadManager: MacOSDownloadManager? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    /**
     * Resolve the video URL for a given episode.
     * Priority:
     * 1. Local file (if downloaded) → serve via MacOSHttpServer
     * 2. Source API (if extension is available) — uses the full [SEpisode] from
     *    the source so all fields (name, episode_number, url, etc.) are populated
     * 3. null (fallback mode)
     */
    private suspend fun resolveVideoUrl(
        sEpisode: SEpisode?,
        episodeNumber: Double,
        httpServer: MacOSHttpServer?,
        onQuality: (resolution: Int?, label: String?) -> Unit = { _, _ -> },
        onError: ((String) -> Unit)? = null,
    ): String? {
        // Priority 1: Local download
        if (downloadManager != null && episodeNumber > 0) {
            val localFile = downloadManager.getLocalFile(animeId, episodeNumber)
            if (localFile != null && httpServer != null && httpServer.isRunning) {
                val streamUrl = httpServer.getStreamUrl(localFile)
                if (streamUrl != null) {
                    return streamUrl
                }
            }
        }

        // Priority 2: Source API — use full SEpisode with all fields populated
        if (sourceId != null && sEpisode != null) {
            val episodeUrl = try { sEpisode.url } catch (_: Exception) { null }
            val source = extensionManager?.getSource(sourceId)
            if (source != null && episodeUrl != null) {
                try {
                    val videos = source.getVideoList(sEpisode)
                    if (videos.isNotEmpty()) {
                        val best = videos.firstOrNull { it.preferred } ?: videos.first()
                        onQuality(best.resolution, best.videoTitle.takeIf { it.isNotBlank() })
                        UIActionLogger.logVideoResolution(sourceId, episodeUrl, best.videoUrl.take(80))
                        return best.videoUrl
                    } else {
                        UIActionLogger.logVideoResolution(sourceId, episodeUrl, "no_videos_found")
                    }
                } catch (e: Exception) {
                    UIActionLogger.logVideoResolution(sourceId, episodeUrl, "error: ${e::class.simpleName}: ${e.message?.take(50)}")
                    onError?.invoke("Video resolution failed: ${e.message?.take(60)}")
                    // Fall through
                }
            }
        }

        return null
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val historyRepo = LocalHistoryRepository.current

        // Initialize the player view model (Phase 6)
        val playerViewModel = remember { PlayerViewModel() }

        // Create and manage the local HTTP server for serving downloaded files
        // Derive the downloads directory from the first completed download's parent dir,
        // or fall back to a reasonable default.
        val httpServer = remember {
            val videosDir = downloadManager?.let { dm ->
                dm.getLocalFile(animeId, 1.0)?.parentFile
            } ?: File(System.getProperty("user.home"), "Library/Application Support/Anikku/downloads/videos")
            MacOSHttpServer(
                downloadsDir = videosDir,
            ).apply { startServer() }
        }

        // Stop the HTTP server when the screen is disposed
        DisposableEffect(httpServer) {
            onDispose {
                httpServer.stopServer()
            }
        }

        // Collect player state
        val mpvHandle by playerViewModel.handle.collectAsState()
        val softwareRenderer by playerViewModel.renderer.collectAsState()
        val playbackState by playerViewModel.playbackState.collectAsState()
        val currentPosition by playerViewModel.currentPosition.collectAsState()
        val duration by playerViewModel.duration.collectAsState()
        val isPaused by playerViewModel.isPaused.collectAsState()
        val volume by playerViewModel.volume.collectAsState()
        val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
        val brightness by playerViewModel.brightness.collectAsState()
        val contrast by playerViewModel.contrast.collectAsState()
        val saturation by playerViewModel.saturation.collectAsState()
        val gamma by playerViewModel.gamma.collectAsState()
        val subtitleDelay by playerViewModel.subtitleDelay.collectAsState()
        val audioDelay by playerViewModel.audioDelay.collectAsState()
        val aspectRatio by playerViewModel.aspectRatio.collectAsState()
        val videoRotation by playerViewModel.videoRotation.collectAsState()
        val isHflip by playerViewModel.isHflip.collectAsState()
        val isVflip by playerViewModel.isVflip.collectAsState()

        // State: episode data from source
        // Keep the original SEpisode objects so we can pass full data to getVideoList()
        var allEpisodes by remember { mutableStateOf<MutableList<EpisodeModel>>(mutableListOf()) }
        var sourceEpisodes by remember { mutableStateOf<List<SEpisode>>(emptyList()) }
        var currentEpisodeIndex by remember { mutableIntStateOf(0) }
        var animeTitle by remember { mutableStateOf("Unknown") }
        var videoUrlToLoad by remember { mutableStateOf<String?>(null) }
        var videoQualityResolution by remember { mutableStateOf<Int?>(null) }
        var videoQualityLabel by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var isResolvingVideo by remember { mutableStateOf(false) }
        var isOfflinePlayback by remember { mutableStateOf(false) }
        var resolutionStatusText by remember { mutableStateOf("Connecting...") }
        var videoResolutionError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        UIActionLogger.logScreenOpen("PlayerScreen", mapOf(
            "animeId" to animeId, "episodeId" to episodeId, "sourceId" to sourceId
        ))

        // On mount: initialize mpv and fetch episode data
        LaunchedEffect(Unit) {
            playerViewModel.initialize()

            // Step 1: Try to fetch episodes from the source
            var usedSource = false
            if (sourceId != null && episodeUrl != null) {
                try {
                    resolutionStatusText = "Fetching episode list..."
                    val source = extensionManager?.getSource(sourceId)
                    if (source != null) {
                        val sAnime = eu.kanade.tachiyomi.animesource.model.SAnime.create().apply {
                            url = episodeUrl.substringBeforeLast("/")
                        }
                        resolutionStatusText = "Loading episodes from \"${source.name}\"..."
                        val fetchedEpisodes = source.getEpisodeList(sAnime)
                        sourceEpisodes = fetchedEpisodes
                        allEpisodes = fetchedEpisodes.map { it.toEpisodeModel(animeId) }.toMutableList()
                        val idx = fetchedEpisodes.indexOfFirst { it.url == episodeUrl }
                        if (idx >= 0) currentEpisodeIndex = idx
                        usedSource = true
                    }
                } catch (e: Exception) {
                    toastHost.show("Failed to fetch episodes: ${e.message?.take(60)}", ToastDuration.LONG)
                }
            }

            // Step 2: Determine starting episode number for local-file check
            val currentEpisodeNumber = allEpisodes.getOrNull(currentEpisodeIndex)?.episodeNumber ?: 0.0

            // Step 3: Resolve video URL — use the full SEpisode with all fields populated
            isResolvingVideo = true
            val sourceName = extensionManager?.getSource(sourceId ?: 0)?.name ?: "source"
            resolutionStatusText = "Resolving video from \"$sourceName\"..."
            
            val currentSEpisode = sourceEpisodes.getOrNull(currentEpisodeIndex)
            val resolvedUrl = resolveVideoUrl(
                sEpisode = currentSEpisode,
                episodeNumber = currentEpisodeNumber,
                httpServer = httpServer,
                onQuality = { res, label ->
                    videoQualityResolution = res
                    videoQualityLabel = label
                },
                onError = { msg ->
                    videoResolutionError = msg
                    toastHost.show(msg, ToastDuration.LONG)
                },
            )
            isResolvingVideo = false

            // Determine if this is offline playback
            isOfflinePlayback = downloadManager != null && currentEpisodeNumber > 0 &&
                downloadManager.isDownloaded(animeId, currentEpisodeNumber)

            if (resolvedUrl != null) {
                videoUrlToLoad = resolvedUrl
                resolutionStatusText = "Video resolved — loading into player..."
            } else if (!usedSource && allEpisodes.isEmpty()) {
                // No source data available — show loading completed with no video
                isLoading = false
                resolutionStatusText = ""
            } else {
                // Video resolution failed
                isLoading = false
                resolutionStatusText = ""
            }
        }

        // When video URL is available AND mpv is ready, load it into mpv
        LaunchedEffect(videoUrlToLoad, mpvHandle) {
            val url = videoUrlToLoad
            if (url != null && mpvHandle != null) {
                resolutionStatusText = "Loading video into mpv player..."
                playerViewModel.loadEpisode(url)
            }
        }

        // Transition from loading to playing: when mpv reports playing state,
        // remove the loading screen so the user sees the video
        LaunchedEffect(videoUrlToLoad, playbackState) {
            if (videoUrlToLoad != null && playbackState != PlaybackState.IDLE && playbackState != PlaybackState.ERROR) {
                // Video is loading/buffering/playing — hide the loading screen
                isLoading = false
                resolutionStatusText = ""
            } else if (playbackState == PlaybackState.ERROR) {
                isLoading = false
                resolutionStatusText = ""
                videoResolutionError = "Failed to play video — stream URL may be invalid"
            }
        }

        // Update anime title from episode data if unknown
        LaunchedEffect(allEpisodes) {
            if (animeTitle == "Unknown" && allEpisodes.isNotEmpty() && sourceId != null) {
                val source = extensionManager?.getSource(sourceId)
                if (source != null && source is eu.kanade.tachiyomi.source.CatalogueSource) {
                    // Title stays as "Unknown" when no source is available
                }
            }
        }

        // Save resume position to history
        fun saveResumePosition(episode: EpisodeModel?, position: Long, total: Long) {
            if (episode == null || position <= 0) return
            historyRepo.add(
                HistoryRepository.HistoryEntry(
                    animeId = animeId,
                    episodeId = episode.id,
                    animeTitle = animeTitle,
                    episodeName = episode.name,
                    episodeNumber = episode.episodeNumber,
                    sourceId = sourceId ?: 0L,
                    episodeUrl = episode.url,
                    seenAt = System.currentTimeMillis(),
                    watchDuration = position,
                    lastSecondSeen = position,
                    totalSeconds = total,
                )
            )
        }

        // Save resume position on shutdown
        fun saveCurrentPosition() {
            val ep = allEpisodes.getOrNull(currentEpisodeIndex)
            if (ep != null && duration > 0 && currentPosition > 0) {
                saveResumePosition(ep, currentPosition.toLong(), duration.toLong())
            }
        }

        // Clean up when the screen is removed — save position + record history
        DisposableEffect(Unit) {
            onDispose {
                saveCurrentPosition()
                playerViewModel.shutdown()
            }
        }

        val currentEpisode = remember(currentEpisodeIndex, allEpisodes) {
            allEpisodes.getOrNull(currentEpisodeIndex)
        }

        PlayerContent(
            playerViewModel = playerViewModel,
            mpvHandle = mpvHandle,
            softwareRenderer = softwareRenderer,
            animeTitle = animeTitle,
            episodes = allEpisodes,
            currentEpisodeIndex = currentEpisodeIndex,
            currentEpisode = currentEpisode,
            playbackState = playbackState,
            currentPosition = currentPosition,
            duration = duration,
            isPaused = isPaused,
            volume = volume,
            playbackSpeed = playbackSpeed,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            gamma = gamma,
            subtitleDelay = subtitleDelay,
            audioDelay = audioDelay,
            aspectRatio = aspectRatio,
            videoRotation = videoRotation,
            isHflip = isHflip,
            isVflip = isVflip,
            isMPVAvailable = playerViewModel.isMPVAvailable,
            isOfflinePlayback = isOfflinePlayback,
            hasVideoUrl = videoUrlToLoad != null,
            isResolvingVideo = isResolvingVideo,
            resolutionStatusText = resolutionStatusText,
            videoResolutionError = videoResolutionError,
            videoQualityResolution = videoQualityResolution,
            videoQualityLabel = videoQualityLabel,
            isLoading = isLoading,
            onBack = { navigator.pop() },
            onNavigateEpisode = { index ->
                if (index in allEpisodes.indices) {
                    // Save resume position for current episode before switching
                    val oldEpisode = allEpisodes.getOrNull(currentEpisodeIndex)
                    if (oldEpisode != null && currentPosition > 0 && duration > 0) {
                        saveResumePosition(oldEpisode, currentPosition.toLong(), duration.toLong())
                    }

                    val oldIndex = currentEpisodeIndex
                    currentEpisodeIndex = index
                    val episode = allEpisodes[index]
                    val direction = if (index > oldIndex) "next" else "previous"
                    toastHost.show("$direction episode: ${String.format("%.0f", episode.episodeNumber)}", ToastDuration.SHORT)

                    // Resolve new episode's video URL — use full SEpisode with all fields
                    scope.launch {
                        isOfflinePlayback = downloadManager != null &&
                            episode.episodeNumber > 0 &&
                            downloadManager.isDownloaded(animeId, episode.episodeNumber)

                        val se = sourceEpisodes.getOrNull(index)
                        val resolvedUrl = resolveVideoUrl(
                            sEpisode = se,
                            episodeNumber = episode.episodeNumber,
                            httpServer = httpServer,
                            onQuality = { res, label ->
                                videoQualityResolution = res
                                videoQualityLabel = label
                            },
                            onError = { msg -> toastHost.show(msg, ToastDuration.LONG) },
                        )
                        if (resolvedUrl != null) {
                            videoUrlToLoad = resolvedUrl
                        } else if (sourceId != null && episode.url != null) {
                            // Fall back to source API — build full SEpisode from EpisodeModel
                            val source = extensionManager?.getSource(sourceId)
                            if (source != null) {
                                try {
                                    val sEpisode = SEpisode.create().apply {
                                        url = episode.url
                                        name = episode.name
                                        episode_number = episode.episodeNumber.toFloat()
                                        date_upload = episode.dateUpload
                                        scanlator = episode.scanlator
                                    }
                                    val videos = source.getVideoList(sEpisode)
                                    if (videos.isNotEmpty()) {
                                        videoUrlToLoad = videos.first().videoUrl
                                    }
                                } catch (_: Exception) {
                                    toastHost.show("Failed to load episode", ToastDuration.SHORT)
                                }
                            }
                        } else {
                            toastHost.show("No video source available", ToastDuration.SHORT)
                        }
                    }
                } else if (index > currentEpisodeIndex) {
                    toastHost.show("No next episode available", ToastDuration.SHORT)
                } else if (index < currentEpisodeIndex) {
                    toastHost.show("No previous episode available", ToastDuration.SHORT)
                }
            },
            onTogglePlay = { playerViewModel.togglePause() },
            onSeekTo = { seconds -> playerViewModel.seekTo(seconds) },
            onSeekRelative = { offset -> playerViewModel.seekRelative(offset) },
            onSetVolume = { vol -> playerViewModel.setVolume(vol) },
            onTakeScreenshot = {
                val result = playerViewModel.takeScreenshot()
                if (result != null) {
                    toastHost.show("Screenshot captured", ToastDuration.SHORT)
                } else {
                    toastHost.show("Screenshot failed", ToastDuration.SHORT)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerContent(
    playerViewModel: PlayerViewModel? = null,
    mpvHandle: com.sun.jna.Pointer? = null,
    softwareRenderer: MPVSoftwareRenderer? = null,
    animeTitle: String,
    episodes: List<EpisodeModel>,
    currentEpisodeIndex: Int,
    currentEpisode: EpisodeModel?,
    playbackState: PlaybackState = PlaybackState.IDLE,
    currentPosition: Double = 0.0,
    duration: Double = 0.0,
    isPaused: Boolean = true,
    volume: Int = 100,
    playbackSpeed: Double = 1.0,
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f,
    gamma: Float = 1f,
    subtitleDelay: Double = 0.0,
    audioDelay: Double = 0.0,
    aspectRatio: String = "-1",
    videoRotation: Int = 0,
    isHflip: Boolean = false,
    isVflip: Boolean = false,
    isMPVAvailable: Boolean = false,
    isOfflinePlayback: Boolean = false,
    hasVideoUrl: Boolean = false,
    isResolvingVideo: Boolean = false,
    resolutionStatusText: String = "",
    videoResolutionError: String? = null,
    isLive: Boolean = false,
    videoQualityResolution: Int? = null,
    videoQualityLabel: String? = null,
    isLoading: Boolean = true,
    onBack: () -> Unit,
    onNavigateEpisode: (Int) -> Unit,
    onTogglePlay: () -> Unit = {},
    onSeekTo: (Double) -> Unit = {},
    onSeekRelative: (Double) -> Unit = {},
    onSetVolume: (Int) -> Unit = {},
    onTakeScreenshot: () -> Unit = {},
) {
    // --- Player state ---
    var isPlaying by remember { mutableStateOf(!isPaused) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableLongStateOf(currentEpisode?.lastSecondSeen ?: 0L) }
    var totalSeconds by remember { mutableLongStateOf(currentEpisode?.totalSeconds?.coerceAtLeast(1) ?: 1440L) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    // Settings panel visibility
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSpeedPanel by remember { mutableStateOf(false) }
    var showAudioPanel by remember { mutableStateOf(false) }
    var showSubtitlePanel by remember { mutableStateOf(false) }
    var showEqualizerPanel by remember { mutableStateOf(false) }
    var showAspectRatioPanel by remember { mutableStateOf(false) }
    var showVideoFilterPanel by remember { mutableStateOf(false) }

    // Sync with mpv state when available
    LaunchedEffect(isPaused) { isPlaying = !isPaused }
    LaunchedEffect(currentPosition) {
        if (duration > 0 && currentPosition > 0) {
            elapsedSeconds = currentPosition.toLong()
            totalSeconds = duration.toLong()
            seekFraction = (currentPosition / duration).toFloat().coerceIn(0f, 1f)
        }
    }

    // If episode changes, reset progress
    LaunchedEffect(currentEpisode?.id) {
        if (duration <= 0) {
            elapsedSeconds = currentEpisode?.lastSecondSeen ?: 0L
            totalSeconds = currentEpisode?.totalSeconds?.coerceAtLeast(1) ?: 1440L
            seekFraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
        }
    }

    // Auto-hide controls after 3 seconds of playback
    LaunchedEffect(isPlaying, isControlsVisible) {
        if (isPlaying && isControlsVisible) {
            delay(3000L)
            isControlsVisible = false
        }
    }

    val toastHost = LocalToastHost.current

    // Mock simulation fallback (when mpv is not available and no real video loaded)
    // Wait 3 seconds for mpv to initialize before entering mock mode, since
    // mpv initialization (lib loading, handle creation, option config) can
    // take 1-2 seconds and isMPVAvailable may be false during that window.
    LaunchedEffect(isPlaying, playerViewModel?.playbackState?.value) {
        if (isPlaying && !isMPVAvailable) {
            // Give mpv a chance to initialize before entering mock mode
            delay(3000L)
            // Check again — mpv might have initialized during the delay
            if (isMPVAvailable) return@LaunchedEffect
            // Also check if a real video is loaded (playbackState would be PLAYING or LOADING)
            val state = playerViewModel?.playbackState?.value
            if (state == PlaybackState.LOADING || state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING) {
                return@LaunchedEffect
            }
            while (true) {
                delay(1000L)
                elapsedSeconds = (elapsedSeconds + 1).coerceAtMost(totalSeconds)
                seekFraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
                if (elapsedSeconds >= totalSeconds) {
                    isPlaying = false
                    toastHost.show("Episode complete", ToastDuration.SHORT)
                    break
                }
            }
        }
    }

    // Keyboard shortcuts
    val interactionSource = remember { MutableInteractionSource() }

    // Show loading state
    if (isLoading || isResolvingVideo) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White.copy(alpha = 0.6f),
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    if (resolutionStatusText.isNotBlank()) resolutionStatusText else "Loading episode...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f),
                )
                if (isResolvingVideo) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This may take a few seconds — the source is fetching video streams",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
        return
    }

    // Show error state when source was needed but not available and we have no episodes
    // Also show when no video URL was resolved despite having a source available
    // Show error state when video URL resolution failed (mpv is available but no video loaded)
    val videoResolutionFailed = !hasVideoUrl && currentEpisode != null && !isLoading && playbackState == PlaybackState.IDLE
    if (!isMPVAvailable && episodes.isEmpty() && currentEpisode == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.2f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    animeTitle.ifBlank { "Episode not found" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Could not load episode. The source extension may need to be updated or reinstalled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(24.dp))
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }
            }
        }
        return
    }

    // Show video resolution failure state with actionable info
    if (videoResolutionFailed) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.15f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    animeTitle.ifBlank { "Unknown Anime" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(4.dp))
                if (currentEpisode != null) {
                    Text(
                        "Episode ${String.format("%.0f", currentEpisode.episodeNumber)} — ${currentEpisode.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Could not resolve video URL",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "This extension's video API may differ from what the app expects. Try a different source.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Check ~/Library/Application Support/Anikku/logs/actions.log for VIDEO_RESOLVE details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.2f),
                )
                Spacer(Modifier.height(24.dp))
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                isControlsVisible = !isControlsVisible
            }
            .onKeyEvent { event ->
                when {
                    event.key == Key.Spacebar -> {
                        onTogglePlay()
                        isPlaying = !isPlaying
                        isControlsVisible = true
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        onSeekRelative(-10.0)
                        elapsedSeconds = (elapsedSeconds - 10).coerceAtLeast(0)
                        seekFraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        onSeekRelative(10.0)
                        elapsedSeconds = (elapsedSeconds + 10).coerceAtMost(totalSeconds)
                        seekFraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        onSetVolume((volume + 5).coerceAtMost(200))
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        onSetVolume((volume - 5).coerceAtLeast(0))
                        true
                    }
                    else -> false
                }
            },
    ) {
        // === MPV Video Surface (when available) ===
        if (isMPVAvailable && mpvHandle != null) {
            MPVVideoSurface(
                mpvHandle = mpvHandle,
                renderer = softwareRenderer,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // === Fallback: Video area placeholder ===
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (currentEpisode != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(animeTitle, style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Episode ${String.format("%.0f", currentEpisode.episodeNumber)}", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.2f))
                        Spacer(Modifier.height(16.dp))
                        val mpvStatus = when {
                            !MPVLib.isAvailable && !isMPVAvailable -> "Install mpv: brew install mpv"
                            playbackState == PlaybackState.LOADING -> "Loading..."
                            playbackState == PlaybackState.BUFFERING -> "Buffering..."
                            else -> "Video area — mpv ready"
                        }
                        Text(mpvStatus, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.15f))
                    }
                } else {
                    Text("No episode selected", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.4f))
                }
            }
        }

        // === Gradient overlays for controls ===
        AnimatedVisibility(visible = isControlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                ),
            ))
        }
        AnimatedVisibility(visible = isControlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(240.dp).background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                ),
            ))
        }

        // === Top bar ===
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(animeTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (currentEpisode != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Episode ${String.format("%.0f", currentEpisode.episodeNumber)} — ${currentEpisode.name}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (isOfflinePlayback) {
                                    Spacer(Modifier.width(8.dp))
                                    OfflineBadge()
                                }
                                videoQualityResolution?.let {
                                    Spacer(Modifier.width(6.dp))
                                    VideoQualityBadge(
                                        resolution = it,
                                        label = videoQualityLabel,
                                    )
                                }
                                val stateBadgeState = playbackState
                                if ((stateBadgeState != PlaybackState.IDLE && stateBadgeState != PlaybackState.PLAYING) || isLive) {
                                    Spacer(Modifier.width(6.dp))
                                    PlaybackStateBadge(
                                        playbackState = stateBadgeState,
                                        isLive = isLive,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        TransportIconButton(icon = Icons.Outlined.Settings, description = "Settings", onClick = { showSettingsMenu = true })
                        DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                            DropdownMenuItem(text = { Text("Playback Speed") }, onClick = { showSettingsMenu = false; showSpeedPanel = true })
                            DropdownMenuItem(text = { Text("Audio Track") }, onClick = { showSettingsMenu = false; showAudioPanel = true })
                            DropdownMenuItem(text = { Text("Subtitles") }, onClick = { showSettingsMenu = false; showSubtitlePanel = true })
                            DropdownMenuItem(text = { Text("Equalizer") }, onClick = { showSettingsMenu = false; showEqualizerPanel = true })
                            DropdownMenuItem(text = { Text("Aspect Ratio") }, onClick = { showSettingsMenu = false; showAspectRatioPanel = true })
                            DropdownMenuItem(text = { Text("Video Filters") }, onClick = { showSettingsMenu = false; showVideoFilterPanel = true })
                        }
                    }
                    if (isMPVAvailable) {
                        TransportIconButton(icon = Icons.Outlined.CameraAlt, description = "Take screenshot", onClick = onTakeScreenshot)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, navigationIconContentColor = Color.White),
            )
        }

        // === Offline checkmark animation ===
        OfflineCheckmarkAnimation(
            isOfflinePlayback = isOfflinePlayback,
            modifier = Modifier.align(Alignment.Center).padding(bottom = 80.dp),
        )

        // === Center play/pause ===
        AnimatedVisibility(visible = !isPlaying && isControlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            IconButton(onClick = { onTogglePlay(); isPlaying = true }, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Outlined.PlayCircle, contentDescription = "Play", tint = Color.White, modifier = Modifier.fillMaxSize())
            }
        }

        // === Bottom controls ===
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerTransportControls(
                currentPositionSeconds = elapsedSeconds,
                totalDurationSeconds = totalSeconds,
                isPlaying = isPlaying,
                playbackState = playbackState,
                currentEpisodeIndex = currentEpisodeIndex,
                episodeCount = episodes.size,
                volume = volume,
                showVolume = isMPVAvailable,
                onTogglePlay = { onTogglePlay(); isPlaying = !isPlaying },
                onSeek = { seekFraction = it },
                onSeekEnd = { fraction ->
                    val newSeconds = (fraction * totalSeconds).toLong()
                    elapsedSeconds = newSeconds
                    onSeekTo(newSeconds.toDouble())
                },
                onSeekRelative = { offset ->
                    onSeekRelative(offset)
                    elapsedSeconds = (elapsedSeconds + offset.toLong()).coerceIn(0, totalSeconds)
                    seekFraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
                },
                onNavigateEpisode = onNavigateEpisode,
            )
        }

        // === Keyboard shortcut hint ===
        AnimatedVisibility(
            visible = isControlsVisible && !isPlaying,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (if (isMPVAvailable) 180 else 120).dp),
        ) {
            Text("SPACE play/pause · ←→ seek · ↑↓ volume", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
        }

        // === Settings panel overlays ===
        val isAnyPanelOpen = showSpeedPanel || showAudioPanel || showSubtitlePanel || showEqualizerPanel || showAspectRatioPanel || showVideoFilterPanel
        if (isAnyPanelOpen) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { showSpeedPanel = false; showAudioPanel = false; showSubtitlePanel = false; showEqualizerPanel = false; showAspectRatioPanel = false; showVideoFilterPanel = false })
        }

        AnimatedVisibility(visible = showSpeedPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerSpeedPanel(currentSpeed = playbackSpeed.toFloat(), onSpeedChange = { playerViewModel?.setSpeed(it.toDouble()) }, onDismiss = { showSpeedPanel = false })
        }
        AnimatedVisibility(visible = showAudioPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerAudioTrackPanel(tracks = playerViewModel?.audioTracks?.value?.map { it.title } ?: emptyList(), currentTrackIndex = playerViewModel?.selectedAudioTrack?.value ?: -1, audioDelay = audioDelay, onTrackSelected = { playerViewModel?.selectAudioTrack(it) }, onDelayChange = { playerViewModel?.setAudioDelay(it) }, onDismiss = { showAudioPanel = false })
        }
        AnimatedVisibility(visible = showSubtitlePanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerSubtitleTrackPanel(tracks = playerViewModel?.subtitleTracks?.value?.map { it.title } ?: emptyList(), currentTrackIndex = playerViewModel?.selectedSubtitleTrack?.value ?: -1, subtitleDelay = subtitleDelay, onTrackSelected = { playerViewModel?.selectSubtitleTrack(it) }, onDelayChange = { playerViewModel?.setSubtitleDelay(it) }, onDismiss = { showSubtitlePanel = false })
        }
        AnimatedVisibility(visible = showEqualizerPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerEqualizerPanel(brightness = brightness, contrast = contrast, saturation = saturation, gamma = gamma, onBrightnessChange = { playerViewModel?.setBrightness(it) }, onContrastChange = { playerViewModel?.setContrast(it) }, onSaturationChange = { playerViewModel?.setSaturation(it) }, onGammaChange = { playerViewModel?.setGamma(it) }, onReset = { playerViewModel?.resetEqualizer() }, onDismiss = { showEqualizerPanel = false })
        }
        AnimatedVisibility(visible = showAspectRatioPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerAspectRatioPanel(currentRatio = aspectRatio, onRatioChange = { playerViewModel?.setAspectRatio(it) }, onDismiss = { showAspectRatioPanel = false })
        }
        AnimatedVisibility(visible = showVideoFilterPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerVideoFilterPanel(currentRotation = videoRotation, isHflip = isHflip, isVflip = isVflip, onRotationChange = { playerViewModel?.setVideoRotation(it) }, onToggleHflip = { playerViewModel?.toggleHflip() }, onToggleVflip = { playerViewModel?.toggleVflip() }, onDismiss = { showVideoFilterPanel = false })
        }
    }
}
