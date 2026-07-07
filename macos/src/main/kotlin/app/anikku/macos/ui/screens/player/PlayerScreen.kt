package app.anikku.macos.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.anikku.macos.player.formatDuration
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.models.EpisodeModel
import app.anikku.macos.ui.screens.models.MockData
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.delay

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
 * If mpv is not available, falls back gracefully to the Phase 5 UI shell
 * with mock simulation for testing.
 *
 * @param animeId     The anime these episodes belong to.
 * @param episodeId   The episode to start playing.
 */
data class PlayerScreen(
    val animeId: Long,
    val episodeId: Long,
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

        // Look up data from MockData
        val anime = remember { MockData.sampleAnime.find { it.id == animeId } }
        val allEpisodes = remember { MockData.sampleEpisodes.filter { it.animeId == animeId } }
        var currentEpisodeIndex by remember {
            mutableStateOf(allEpisodes.indexOfFirst { it.id == episodeId }.coerceAtLeast(0))
        }
        val currentEpisode = remember(currentEpisodeIndex) { allEpisodes.getOrNull(currentEpisodeIndex) }

        // Initialize mpv when the screen first appears
        LaunchedEffect(Unit) {
            playerViewModel.initialize()
        }

        // Clean up when the screen is removed
        DisposableEffect(Unit) {
            onDispose {
                playerViewModel.shutdown()
            }
        }

        PlayerContent(
            playerViewModel = playerViewModel,
            mpvHandle = mpvHandle,
            animeTitle = anime?.title ?: "Unknown",
            episodes = allEpisodes,
            currentEpisodeIndex = currentEpisodeIndex,
            currentEpisode = currentEpisode,
            playbackState = playbackState,
            currentPosition = currentPosition,
            duration = duration,
            isPaused = isPaused,
            volume = volume,
            isMPVAvailable = playerViewModel.isMPVAvailable,
            onBack = { navigator.pop() },
            onNavigateEpisode = { index ->
                if (index in allEpisodes.indices) {
                    currentEpisodeIndex = index
                    val episode = allEpisodes[index]
                    val direction = if (index > currentEpisodeIndex) "next" else "previous"
                    toastHost.show("$direction episode: ${String.format("%.0f", episode.episodeNumber)}", ToastDuration.SHORT)
                    // Load the new episode URL via local HTTP server
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
    isMPVAvailable: Boolean = false,
    onBack: () -> Unit,
    onNavigateEpisode: (Int) -> Unit,
    onTogglePlay: () -> Unit = {},
    onSeekTo: (Double) -> Unit = {},
    onSeekRelative: (Double) -> Unit = {},
    onSetVolume: (Int) -> Unit = {},
    onTakeScreenshot: () -> Unit = {},
) {
    // --- Player state (falls back to mock simulation when mpv unavailable) ---
    var isPlaying by remember { mutableStateOf(!isPaused) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableLongStateOf(currentEpisode?.lastSecondSeen ?: 0L) }
    var totalSeconds by remember { mutableLongStateOf(currentEpisode?.totalSeconds?.coerceAtLeast(1) ?: 1440L) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    // Sync with mpv state when available
    LaunchedEffect(isPaused) {
        isPlaying = !isPaused
    }
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

    // Mock simulation fallback (when mpv is not available)
    LaunchedEffect(isPlaying) {
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
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // === Fallback: Video area placeholder (mock mode) ===
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (currentEpisode != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = animeTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Episode ${String.format("%.0f", currentEpisode.episodeNumber)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.2f),
                        )
                        Spacer(Modifier.height(16.dp))
                        val mpvStatus = when {
                            !MPVLib.isAvailable && !isMPVAvailable -> "Install mpv: brew install mpv"
                            playbackState == PlaybackState.LOADING -> "Loading..."
                            playbackState == PlaybackState.BUFFERING -> "Buffering..."
                            else -> "Video area — mpv ready"
                        }
                        Text(
                            text = mpvStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.15f),
                        )
                    }
                } else {
                    Text(
                        text = "No episode selected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        }

        // === Gradient overlays for controls ===
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                        ),
                    ),
            )
        }

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        ),
                    ),
            )
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
                        Text(
                            text = animeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentEpisode != null) {
                            Text(
                                text = "Episode ${String.format("%.0f", currentEpisode.episodeNumber)} — ${currentEpisode.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    // Screenshot button
                    if (isMPVAvailable) {
                        PlayerIconButton(
                            icon = Icons.Outlined.CameraAlt,
                            description = "Take screenshot",
                            onClick = onTakeScreenshot,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                ),
            )
        }

        // === Center play/pause (large, shown when paused) ===
        AnimatedVisibility(
            visible = !isPlaying && isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            IconButton(
                onClick = {
                    onTogglePlay()
                    isPlaying = true
                },
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircle,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // === Bottom controls ===
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                // Seek bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDuration(elapsedSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.width(48.dp),
                    )

                    Slider(
                        value = seekFraction,
                        onValueChange = { seekFraction = it },
                        onValueChangeFinished = {
                            val newSeconds = (seekFraction * totalSeconds).toLong()
                            elapsedSeconds = newSeconds
                            onSeekTo(newSeconds.toDouble())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                    )

                    Text(
                        text = formatDuration(totalSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.width(48.dp),
                    )
                }

                // Volume indicator
                if (isMPVAvailable) {
                    Text(
                        text = "Volume: $volume",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Transport controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Previous episode
                    PlayerIconButton(
                        icon = Icons.Filled.SkipPrevious,
                        description = "Previous episode",
                        enabled = currentEpisodeIndex > 0,
                        onClick = { onNavigateEpisode(currentEpisodeIndex - 1) },
                    )

                    Spacer(Modifier.width(16.dp))

                    // Rewind 10s
                    PlayerIconButton(
                        icon = Icons.Outlined.FastRewind,
                        description = "Rewind 10 seconds",
                        onClick = {
                            onSeekRelative(-10.0)
                            elapsedSeconds = (elapsedSeconds - 10).coerceAtLeast(0)
                            seekFraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
                        },
                    )

                    Spacer(Modifier.width(24.dp))

                    // Play / Pause
                    IconButton(
                        onClick = {
                            onTogglePlay()
                            isPlaying = !isPlaying
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(Modifier.width(24.dp))

                    // Forward 10s
                    PlayerIconButton(
                        icon = Icons.Outlined.FastForward,
                        description = "Forward 10 seconds",
                        onClick = {
                            onSeekRelative(10.0)
                            elapsedSeconds = (elapsedSeconds + 10).coerceAtMost(totalSeconds)
                            seekFraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
                        },
                    )

                    Spacer(Modifier.width(16.dp))

                    // Next episode
                    PlayerIconButton(
                        icon = Icons.Filled.SkipNext,
                        description = "Next episode",
                        enabled = currentEpisodeIndex < episodes.size - 1,
                        onClick = { onNavigateEpisode(currentEpisodeIndex + 1) },
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Episode pill selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    episodes.forEachIndexed { index, _ ->
                        val isSelected = index == currentEpisodeIndex
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(
                                    width = if (isSelected) 24.dp else 8.dp,
                                    height = 4.dp,
                                )
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.3f),
                                )
                                .clickable { onNavigateEpisode(index) },
                        )
                    }
                }

                // Playback status
                if (isMPVAvailable) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (playbackState) {
                            PlaybackState.LOADING -> "Loading..."
                            PlaybackState.BUFFERING -> "Buffering..."
                            PlaybackState.ERROR -> "Playback error"
                            PlaybackState.ENDED -> "Episode ended"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }

        // === Keyboard shortcut hint ===
        AnimatedVisibility(
            visible = isControlsVisible && !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (if (isMPVAvailable) 180 else 120).dp),
        ) {
            Text(
                text = "SPACE play/pause · ←→ seek · ↑↓ volume",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
    }
}

/**
 * Small circular icon button for transport controls.
 */
@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(24.dp),
        )
    }
}


