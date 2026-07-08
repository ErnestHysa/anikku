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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import app.anikku.macos.player.MPVVideoSurface
import app.anikku.macos.player.PlaybackState
import app.anikku.macos.player.PlayerViewModel
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.models.EpisodeModel
import app.anikku.macos.ui.screens.models.MockData
import app.anikku.macos.ui.screens.models.toEpisodeModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Player screen — Phase 6: mpv Integration.
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
 *
 * When [sourceId] and [extensionManager] are provided, fetches video URLs
 * from the extension source API and loads them into mpv for real playback.
 * Falls back to mock simulation when source is not available.
 *
 * @param animeId     The anime these episodes belong to.
 * @param episodeId   The episode to start playing.
 * @param sourceId    Source ID for extension API lookup (optional).
 * @param episodeUrl  The episode URL on the source (required for source API).
 * @param extensionManager Extension manager for source lookup (optional).
 */
data class PlayerScreen(
    val animeId: Long,
    val episodeId: Long,
    val sourceId: Long? = null,
    val episodeUrl: String? = null,
    val extensionManager: MacOSExtensionManager? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current

        // Initialize the player view model (Phase 6)
        val playerViewModel = remember { PlayerViewModel() }

        // Collect player state
        val mpvHandle by playerViewModel.handle.collectAsState()
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

        // State: episode data from source or MockData
        var allEpisodes by remember { mutableStateOf<MutableList<EpisodeModel>>(mutableListOf()) }
        var currentEpisodeIndex by remember { mutableIntStateOf(0) }
        var animeTitle by remember { mutableStateOf("Unknown") }
        var videoUrlToLoad by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var usingFallback by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()

        // On mount: initialize mpv and fetch episode data
        LaunchedEffect(Unit) {
            playerViewModel.initialize()

            // Try to load episode data from source API
            if (sourceId != null && episodeUrl != null) {
                val source = extensionManager?.getSource(sourceId)
                if (source != null) {
                    try {
                        // Create SEpisode for this URL
                        val sEpisode = SEpisode.create().apply { url = episodeUrl }

                        // Fetch video URLs
                        val videos = source.getVideoList(sEpisode)
                        if (videos.isNotEmpty()) {
                            videoUrlToLoad = videos.first().videoUrl
                        }

                        // Fetch episode list for navigation
                        val sAnime = eu.kanade.tachiyomi.animesource.model.SAnime.create().apply {
                            url = episodeUrl.substringBeforeLast("/")
                        }
                        val sourceEpisodes = source.getEpisodeList(sAnime)

                        // Find the index of the current episode
                        val episodeIdx = sourceEpisodes.indexOfFirst { it.url == episodeUrl }
                        if (episodeIdx >= 0) currentEpisodeIndex = episodeIdx

                        // Convert episodes to models
                        allEpisodes = sourceEpisodes.map { it.toEpisodeModel(animeId) }.toMutableList()
                        usingFallback = false
                    } catch (_: Exception) {
                        // Fall through to MockData fallback
                    }
                }
            }

            // Fallback to MockData
            if (allEpisodes.isEmpty()) {
                allEpisodes = MockData.sampleEpisodes.filter { it.animeId == animeId }.toMutableList()
                currentEpisodeIndex = allEpisodes.indexOfFirst { it.id == episodeId }.coerceAtLeast(0)
                animeTitle = MockData.sampleAnime.find { it.id == animeId }?.title ?: "Unknown"
                videoUrlToLoad = null // no real video URL in mock mode
            }

            isLoading = false
        }

        // When video URL is available AND mpv is ready, load it into mpv
        LaunchedEffect(videoUrlToLoad, mpvHandle) {
            val url = videoUrlToLoad
            if (url != null && mpvHandle != null) {
                playerViewModel.loadEpisode(url)
            }
        }

        // Update anime title when episodes change
        LaunchedEffect(allEpisodes) {
            if (usingFallback && animeTitle == "Unknown") {
                animeTitle = MockData.sampleAnime.find { it.id == animeId }?.title ?: "Unknown"
            }
        }

        // Clean up when the screen is removed
        DisposableEffect(Unit) {
            onDispose {
                playerViewModel.shutdown()
            }
        }

        val currentEpisode = remember(currentEpisodeIndex, allEpisodes) {
            allEpisodes.getOrNull(currentEpisodeIndex)
        }

        PlayerContent(
            playerViewModel = playerViewModel,
            mpvHandle = mpvHandle,
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
            isLoading = isLoading,
            onBack = { navigator.pop() },
            onNavigateEpisode = { index ->
                if (index in allEpisodes.indices) {
                    val oldIndex = currentEpisodeIndex
                    currentEpisodeIndex = index
                    val episode = allEpisodes[index]
                    val direction = if (index > oldIndex) "next" else "previous"
                    toastHost.show("$direction episode: ${String.format("%.0f", episode.episodeNumber)}", ToastDuration.SHORT)

                    // If we have source data, load the new episode's video
                    if (sourceId != null && !usingFallback) {
                        scope.launch {
                            val source = extensionManager?.getSource(sourceId)
                            if (source != null) {
                                try {
                                    val sEpisode = SEpisode.create().apply { url = episode.url ?: "" }
                                    val videos = source.getVideoList(sEpisode)
                                    if (videos.isNotEmpty()) {
                                        videoUrlToLoad = videos.first().videoUrl
                                    }
                                } catch (_: Exception) {
                                    toastHost.show("Failed to load episode", ToastDuration.SHORT)
                                }
                            }
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
    LaunchedEffect(isPlaying, playerViewModel?.playbackState?.value) {
        if (isPlaying && !isMPVAvailable) {
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
    if (isLoading) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Loading episode...", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.5f))
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
            MPVVideoSurface(mpvHandle = mpvHandle, modifier = Modifier.fillMaxSize())
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
                            Text("Episode ${String.format("%.0f", currentEpisode.episodeNumber)} — ${currentEpisode.name}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
