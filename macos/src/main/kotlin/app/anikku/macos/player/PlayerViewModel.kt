package app.anikku.macos.player

import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Central player state and control model.
 *
 * Manages playback lifecycle, position tracking, audio/subtitle tracks,
 * and episode navigation. Communicates with mpv via [MPVLib] and
 * processes events via [MPVEventLoop].
 *
 * ## Architecture
 *
 * ```
 * PlayerViewModel ←→ MPVEventLoop (event processing)
 *        ↕
 *   MPVLib (native calls via JNA)
 *        ↕
 *   libmpv (video decoding + rendering)
 * ```
 *
 * ## Usage
 *
 * ```kotlin
 * val player = remember { PlayerViewModel() }
 *
 * // Observe state
 * val state by player.playbackState.collectAsState()
 *
 * // Control playback
 * player.loadEpisode(videoUrl)
 * player.togglePause()
 * player.seekTo(120.0)
 * ```
 */
class PlayerViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Current mpv handle (null when not initialized). */
    private var mpvHandle: Pointer? = null

    /** Event loop for processing mpv events. */
    private var eventLoop: MPVEventLoop? = null

    /** Current video URL being played. */
    private var currentUrl: String? = null

    // -------------------------------------------------------------------------
    // Observable state
    // -------------------------------------------------------------------------

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0.0)
    val currentPosition: StateFlow<Double> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _isPaused = MutableStateFlow(true)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0)
    val playbackSpeed: StateFlow<Double> = _playbackSpeed.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks.asStateFlow()

    private val _selectedAudioTrack = MutableStateFlow(-1)
    val selectedAudioTrack: StateFlow<Int> = _selectedAudioTrack.asStateFlow()

    private val _selectedSubtitleTrack = MutableStateFlow(-1)
    val selectedSubtitleTrack: StateFlow<Int> = _selectedSubtitleTrack.asStateFlow()

    /** Whether mpv is available on this system. */
    val isMPVAvailable: Boolean get() = MPVLib.isAvailable

    // -------------------------------------------------------------------------
    // Initialization & Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialize the mpv core and event loop.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun initialize() {
        if (mpvHandle != null) return

        MPVLib.initialize()
        if (!MPVLib.isAvailable) {
            _playbackState.value = PlaybackState.ERROR
            logger.warn { "MPV not available — player will operate in mock mode" }
            return
        }

        try {
            val handle = MPVLib.create()
            mpvHandle = handle

            // Configure mpv
            configureMPV(handle)

            val result = MPVLib.initialize(handle)
            if (result < 0) {
                logger.error { "mpv_initialize failed with code: $result" }
                _playbackState.value = PlaybackState.ERROR
                return
            }

            // Start event loop
            val loop = MPVEventLoop(handle)
            eventLoop = loop
            loop.observeProperty("time-pos")
            loop.observeProperty("duration")
            loop.observeProperty("pause")
            loop.observeProperty("volume")
            loop.start()

            // Listen for property changes
            scope.launch {
                loop.propertyChanges.collect { change ->
                    when (change.name) {
                        "time-pos" -> updatePosition()
                        "duration" -> updateDuration()
                        "pause" -> updatePauseState()
                        "volume" -> updateVolume()
                    }
                }
            }

            // Listen for events
            scope.launch {
                loop.events.collect { event ->
                    when (event.eventId) {
                        MPVLib.MPV_EVENT_FILE_LOADED -> {
                            _playbackState.value = PlaybackState.PLAYING
                            _isPaused.value = false
                            updateDuration()
                        }
                        MPVLib.MPV_EVENT_END_FILE -> {
                            _playbackState.value = PlaybackState.ENDED
                        }
                        MPVLib.MPV_EVENT_VIDEO_RECONFIG -> {
                            logger.debug { "Video reconfig event" }
                        }
                        MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                            _playbackState.value = PlaybackState.PLAYING
                        }
                        MPVLib.MPV_EVENT_SEEK -> {
                            _playbackState.value = PlaybackState.SEEKING
                        }
                        MPVLib.MPV_EVENT_SHUTDOWN -> {
                            _playbackState.value = PlaybackState.IDLE
                        }
                    }
                }
            }

            // Periodic position updates
            scope.launch {
                while (true) {
                    delay(500)
                    if (_playbackState.value == PlaybackState.PLAYING) {
                        updatePosition()
                    }
                }
            }

            _playbackState.value = PlaybackState.IDLE
            logger.info { "MPV player initialized successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize MPV player" }
            _playbackState.value = PlaybackState.ERROR
        }
    }

    /**
     * Configure mpv options before initialization.
     */
    private fun configureMPV(handle: Pointer) {
        try {
            // Video output configuration
            MPVLib.setOptionString(handle, "vo", "libmpv")
            MPVLib.setOptionString(handle, "gpu-context", "none")
            MPVLib.setOptionString(handle, "hwdec", "videotoolbox")
            MPVLib.setOptionString(handle, "cache", "yes")
            MPVLib.setOptionString(handle, "cache-secs", "30")
            MPVLib.setOptionString(handle, "demuxer-max-bytes", "150M")
            MPVLib.setOptionString(handle, "demuxer-max-back-bytes", "50M")

            // Audio configuration
            MPVLib.setOptionString(handle, "audio-file-auto", "no")

            // Subtitle configuration
            MPVLib.setOptionString(handle, "sub-auto", "fuzzy")
            MPVLib.setOptionString(handle, "sub-file-auto", "no")

            // OSD configuration
            MPVLib.setOptionString(handle, "osd-level", "0")

            // Keep open on end
            MPVLib.setOptionString(handle, "keep-open", "yes")

            // Screenshot configuration
            MPVLib.setOptionString(handle, "screenshot-format", "png")
            MPVLib.setOptionString(handle, "screenshot-template", "anikku-screenshot-%n")
        } catch (e: Exception) {
            logger.warn(e) { "Failed to set mpv options" }
        }
    }

    /**
     * Shut down the player, clean up mpv resources.
     */
    fun shutdown() {
        eventLoop?.stop()
        mpvHandle?.let { handle ->
            try {
                MPVLib.command(handle, "quit")
            } catch (_: Exception) { }
            MPVLib.destroy(handle)
        }
        mpvHandle = null
        eventLoop = null
        _playbackState.value = PlaybackState.IDLE
        logger.info { "MPV player shut down" }
    }

    // -------------------------------------------------------------------------
    // Playback Controls
    // -------------------------------------------------------------------------

    /**
     * Load and play a video URL.
     * @param url The video URL to play (can be an http:// or file:// URI)
     */
    fun loadEpisode(url: String) {
        val handle = mpvHandle ?: run {
            logger.warn { "Cannot load episode: mpv not initialized" }
            _playbackState.value = PlaybackState.ERROR
            return
        }

        currentUrl = url
        _playbackState.value = PlaybackState.LOADING
        logger.info { "Loading episode: $url" }

        try {
            MPVLib.command(handle, "loadfile", url, "replace")
        } catch (e: Exception) {
            logger.error(e) { "Failed to load episode: $url" }
            _playbackState.value = PlaybackState.ERROR
        }
    }

    /**
     * Toggle between play and pause.
     */
    fun togglePause() {
        val handle = mpvHandle ?: return
        try {
            val paused = MPVLib.getPropertyFlag(handle, "pause", default = true)
            MPVLib.setPropertyString(handle, "pause", if (paused) "no" else "yes")
            _isPaused.value = !paused
        } catch (e: Exception) {
            logger.warn(e) { "Failed to toggle pause" }
        }
    }

    /**
     * Seek to an absolute position in seconds.
     */
    fun seekTo(seconds: Double) {
        val handle = mpvHandle ?: return
        try {
            MPVLib.setPropertyDouble(handle, "time-pos", seconds.coerceAtLeast(0.0))
        } catch (e: Exception) {
            logger.warn(e) { "Failed to seek to $seconds" }
        }
    }

    /**
     * Seek relative to the current position.
     * @param offset Offset in seconds (positive = forward, negative = backward).
     */
    fun seekRelative(offset: Double) {
        seekTo(currentPosition.value + offset)
    }

    /**
     * Set volume (0–200).
     */
    fun setVolume(vol: Int) {
        val handle = mpvHandle ?: return
        val clamped = vol.coerceIn(0, 200)
        try {
            MPVLib.setPropertyInt(handle, "volume", clamped)
            _volume.value = clamped
        } catch (e: Exception) {
            logger.warn(e) { "Failed to set volume" }
        }
    }

    /**
     * Set playback speed.
     */
    fun setSpeed(speed: Double) {
        val handle = mpvHandle ?: return
        val clamped = speed.coerceIn(0.25, 4.0)
        try {
            MPVLib.setPropertyDouble(handle, "speed", clamped)
            _playbackSpeed.value = clamped
        } catch (e: Exception) {
            logger.warn(e) { "Failed to set speed" }
        }
    }

    /**
     * Toggle fullscreen mode.
     */
    fun toggleFullscreen() {
        val handle = mpvHandle ?: return
        try {
            val fs = MPVLib.getPropertyFlag(handle, "fullscreen", default = false)
            MPVLib.setPropertyString(handle, "fullscreen", if (fs) "no" else "yes")
            _isFullscreen.value = !fs
        } catch (e: Exception) {
            // Fullscreen toggling may fail in windowed mode — safe to ignore
            _isFullscreen.value = !_isFullscreen.value
        }
    }

    /**
     * Take a screenshot of the current video frame.
     * @return The file path of the screenshot, or null on failure.
     */
    fun takeScreenshot(): String? {
        val handle = mpvHandle ?: return null
        return try {
            MPVLib.command(handle, "screenshot", "video")
            "Screenshot captured"
        } catch (e: Exception) {
            logger.warn(e) { "Failed to take screenshot" }
            null
        }
    }

    // -------------------------------------------------------------------------
    // Track Management
    // -------------------------------------------------------------------------

    /**
     * Reload track lists from mpv.
     */
    fun refreshTracks() {
        val handle = mpvHandle ?: return

        // Parse track list from mpv property
        val trackList = MPVLib.getPropertyString(handle, "track-list") ?: return

        // Track list is returned as a Lua table string — parse it
        // For now, use a simplified approach: query individual track properties
        try {
            val audioCount = MPVLib.getPropertyInt(handle, "audio-count", 0)
            val subCount = MPVLib.getPropertyInt(handle, "sub-count", 0)

            _audioTracks.value = (0 until audioCount).mapNotNull { index ->
                val lang = MPVLib.getPropertyString(handle, "audio/$index/lang") ?: "unknown"
                val title = MPVLib.getPropertyString(handle, "audio/$index/title") ?: "Track ${index + 1}"
                val codec = MPVLib.getPropertyString(handle, "audio/$index/codec") ?: ""
                TrackInfo(index, title, lang, codec)
            }

            _subtitleTracks.value = (0 until subCount).mapNotNull { index ->
                val lang = MPVLib.getPropertyString(handle, "sub/$index/lang") ?: "unknown"
                val title = MPVLib.getPropertyString(handle, "sub/$index/title") ?: "Track ${index + 1}"
                val codec = MPVLib.getPropertyString(handle, "sub/$index/codec") ?: ""
                TrackInfo(index, title, lang, codec)
            }

            _selectedAudioTrack.value = MPVLib.getPropertyInt(handle, "audio", -1)
            _selectedSubtitleTrack.value = MPVLib.getPropertyInt(handle, "sub", -1)
        } catch (e: Exception) {
            logger.debug(e) { "Failed to parse track list" }
        }
    }

    /**
     * Select an audio track by its mpv ID.
     */
    fun selectAudioTrack(trackId: Int) {
        val handle = mpvHandle ?: return
        try {
            MPVLib.setPropertyInt(handle, "aid", trackId)
            _selectedAudioTrack.value = trackId
        } catch (e: Exception) {
            logger.warn(e) { "Failed to select audio track $trackId" }
        }
    }

    /**
     * Select a subtitle track by its mpv ID.
     */
    fun selectSubtitleTrack(trackId: Int) {
        val handle = mpvHandle ?: return
        try {
            MPVLib.setPropertyInt(handle, "sid", trackId)
            _selectedSubtitleTrack.value = trackId
        } catch (e: Exception) {
            logger.warn(e) { "Failed to select subtitle track $trackId" }
        }
    }

    /**
     * Disable subtitles.
     */
    fun disableSubtitles() {
        selectSubtitleTrack(-1)
    }

    // -------------------------------------------------------------------------
    // Internal state updates
    // -------------------------------------------------------------------------

    private fun updatePosition() {
        val handle = mpvHandle ?: return
        try {
            val pos = MPVLib.getPropertyDouble(handle, "time-pos", 0.0)
            _currentPosition.value = pos.coerceAtLeast(0.0)
        } catch (_: Exception) { }
    }

    private fun updateDuration() {
        val handle = mpvHandle ?: return
        try {
            val dur = MPVLib.getPropertyDouble(handle, "duration", 0.0)
            _duration.value = dur
        } catch (_: Exception) { }
    }

    private fun updatePauseState() {
        val handle = mpvHandle ?: return
        try {
            val paused = MPVLib.getPropertyFlag(handle, "pause", default = true)
            _isPaused.value = paused
            _playbackState.value = if (paused) PlaybackState.PAUSED else PlaybackState.PLAYING
        } catch (_: Exception) { }
    }

    private fun updateVolume() {
        val handle = mpvHandle ?: return
        try {
            _volume.value = MPVLib.getPropertyInt(handle, "volume", 100)
        } catch (_: Exception) { }
    }
}

/**
 * Information about an audio or subtitle track.
 */
data class TrackInfo(
    val id: Int,
    val title: String,
    val language: String,
    val codec: String = "",
)
