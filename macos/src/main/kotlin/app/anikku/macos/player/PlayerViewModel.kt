package app.anikku.macos.player

import app.anikku.macos.platform.logging.CrashReporter
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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

    /** Expose mpv handle reactively for the video surface composable. */
    private val _handle = MutableStateFlow<Pointer?>(null)
    val handle: StateFlow<Pointer?> = _handle.asStateFlow()

    /** Software renderer for pulling decoded frames. */
    private var softwareRenderer: MPVSoftwareRenderer? = null

    /** Expose renderer reactively for the video surface composable. */
    private val _renderer = MutableStateFlow<MPVSoftwareRenderer?>(null)
    val renderer: StateFlow<MPVSoftwareRenderer?> = _renderer.asStateFlow()

    /** Event loop for processing mpv events. */
    private var eventLoop: MPVEventLoop? = null

    /** Tracks the periodic position-update coroutine for cleanup on shutdown. */
    private var positionUpdateJob: Job? = null

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

    // -------------------------------------------------------------------------
    // Video equalizer state
    // -------------------------------------------------------------------------

    private val _brightness = MutableStateFlow(0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _contrast = MutableStateFlow(1f)
    val contrast: StateFlow<Float> = _contrast.asStateFlow()

    private val _saturation = MutableStateFlow(1f)
    val saturation: StateFlow<Float> = _saturation.asStateFlow()

    private val _gamma = MutableStateFlow(1f)
    val gamma: StateFlow<Float> = _gamma.asStateFlow()

    /** Whether mpv is available on this system. */
    val isMPVAvailable: Boolean get() = MPVLib.isAvailable

    // -------------------------------------------------------------------------
    // Initialization & Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialize the mpv core and event loop.
     * Safe to call multiple times — subsequent calls are no-ops.
     *
     * @return true if mpv was successfully initialized, false otherwise.
     */
    fun initialize(): Boolean {
        if (mpvHandle != null) return true

        val mpvLoaded = MPVLib.initialize()
        if (!mpvLoaded) {
            _playbackState.value = PlaybackState.ERROR
            logger.warn { "MPV not available — player will operate in mock mode" }
            CrashReporter.logEvent("MPV init failed", "libmpv could not be loaded")
            return false
        }

        try {
            val handle = MPVLib.create()
            if (handle == null) {
                logger.error { "🚀 MPV_CORE: mpv_create() returned null — cannot create mpv instance. " +
                    "This is likely caused by a locale issue (LC_NUMERIC not \"C\"). " +
                    "Check ~/Library/Logs/Anikku/ for details." }
                _handle.value = null
                mpvHandle = null
                _playbackState.value = PlaybackState.ERROR
                CrashReporter.logEvent("MPV handle null", "mpv_create returned null — locale issue")
                return false
            }
            mpvHandle = handle
            _handle.value = handle
            logger.info { "🚀 MPV_CORE: mpv handle created (${Pointer.nativeValue(handle)})" }

            // Configure mpv options BEFORE mpv_initialize
            val configOk = configureMPV(handle)

            if (!configOk) {
                logger.error { "🚀 MPV_CORE: mpv_set_option failed — mpv may have locale issues" }
                _handle.value = null
                mpvHandle = null
                MPVLib.destroy(handle)
                _playbackState.value = PlaybackState.ERROR
                return false
            }

            val initResult = MPVLib.initialize(handle)
            if (initResult == null || initResult < 0) {
                logger.error { "🚀 MPV_CORE: mpv_initialize failed with code: $initResult" }
                _handle.value = null
                mpvHandle = null
                MPVLib.destroy(handle)
                _playbackState.value = PlaybackState.ERROR
                CrashReporter.logEvent("MPV init failed", "mpv_initialize returned $initResult")
                return false
            }
            logger.info { "🚀 MPV_CORE: mpv initialized successfully (vo=libmpv, hwdec=videotoolbox)" }

            // Create software render context
            val renderer = MPVSoftwareRenderer(handle)
            if (renderer.create()) {
                softwareRenderer = renderer
                _renderer.value = renderer
                logger.info { "🚀 MPV_RENDER: software render context created (MPV_RENDER_API_TYPE_SW)" }
            } else {
                logger.warn { "🚀 MPV_RENDER: software render context creation FAILED — video will not render" }
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
                            logger.info { "🎬 VIDEO_FILE: file loaded into mpv — starting playback" }
                        }
                        MPVLib.MPV_EVENT_END_FILE -> {
                            _playbackState.value = PlaybackState.ENDED
                            logger.info { "🎬 VIDEO_FILE: playback ended" }
                        }
                        MPVLib.MPV_EVENT_VIDEO_RECONFIG -> {
                            val w = MPVLib.getPropertyInt(handle, "dwidth", 0)
                            val h = MPVLib.getPropertyInt(handle, "dheight", 0)
                            if (w > 0 && h > 0) {
                                softwareRenderer?.updateVideoSize(w, h)
                                logger.info { "🎬 VIDEO_RECONFIG: video dimensions detected: ${w}x${h}" }
                            } else {
                                logger.debug { "🎬 VIDEO_RECONFIG: event received (no usable dimensions yet)" }
                            }
                        }
                        MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                            _playbackState.value = PlaybackState.PLAYING
                            logger.info { "🎬 VIDEO_RESTART: playback restarted after seek/load" }
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

            // Periodic position updates — tracked for cleanup on shutdown
            positionUpdateJob = scope.launch {
                while (isActive) {
                    delay(500)
                    if (mpvHandle != null && _playbackState.value == PlaybackState.PLAYING) {
                        updatePosition()
                    }
                }
            }

            _playbackState.value = PlaybackState.IDLE
            logger.info { "🚀 PLAYER_READY: mpv player fully initialized and awaiting video" }
            return true
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize MPV player" }
            CrashReporter.logError("PlayerInit", e.message ?: "", e)
            _playbackState.value = PlaybackState.ERROR
            return false
        }
    }

    /**
     * Configure mpv options before initialization.
     * Returns false if any critical option fails.
     */
    private fun configureMPV(handle: Pointer): Boolean {
        var allOk = true
        val criticalOptions = listOf(
            "vo" to "libmpv",
            "hwdec" to "videotoolbox",
            "cache" to "yes",
            "cache-secs" to "30",
            "demuxer-max-bytes" to "150M",
            "demuxer-max-back-bytes" to "50M",
        )
        val nonCriticalOptions = listOf(
            "audio-file-auto" to "no",
            "sub-auto" to "fuzzy",
            "sub-file-auto" to "no",
            "osd-level" to "0",
            "keep-open" to "yes",
            "screenshot-format" to "png",
            "screenshot-template" to "anikku-screenshot-%n",
        )

        for ((name, value) in criticalOptions) {
            try {
                val result = MPVLib.setOptionString(handle, name, value)
                if (result == null || result < 0) {
                    logger.error { "Failed to set critical mpv option: $name=$value (error: $result)" }
                    allOk = false
                }
            } catch (e: Exception) {
                logger.error(e) { "Exception setting mpv option: $name=$value" }
                allOk = false
            }
        }

        for ((name, value) in nonCriticalOptions) {
            try {
                MPVLib.setOptionString(handle, name, value)
            } catch (e: Exception) {
                logger.debug(e) { "Non-critical mpv option failed: $name=$value" }
            }
        }

        if (!allOk) {
            CrashReporter.logEvent("MPV config failed", "Critical options could not be set")
        }

        return allOk
    }

    /**
     * Shut down the player, clean up mpv resources.
     */
    fun shutdown() {
        logger.info { "🎬 PLAYER_SHUTDOWN: shutting down mpv player..." }
        positionUpdateJob?.cancel()
        positionUpdateJob = null
        eventLoop?.stop()
        softwareRenderer?.dispose()
        _renderer.value = null
        softwareRenderer = null
        mpvHandle?.let { handle ->
            try {
                MPVLib.command(handle, "quit")
            } catch (_: Exception) { }
            MPVLib.destroy(handle)
        }
        _handle.value = null
        mpvHandle = null
        eventLoop = null
        _playbackState.value = PlaybackState.IDLE
        logger.info { "🎬 PLAYER_SHUTDOWN: mpv player shut down complete" }
        CrashReporter.logEvent("Player shutdown")
    }

    // -------------------------------------------------------------------------
    // Playback Controls
    // -------------------------------------------------------------------------

    /**
     * Load and play a video URL with optional HTTP headers.
     *
     * Many streaming sources require specific HTTP headers (like Referer or Origin)
     * to serve the video stream. Without them, the server may reject the request,
     * return a 403, or serve a placeholder file (causing "stuck at seeking" or
     * 1-second playback).
     *
     * This method sets `http-header-fields` on mpv BEFORE calling `loadfile`,
     * so mpv includes the required headers in its HTTP requests to the stream server.
     *
     * @param url The video URL to play (can be an http:// or file:// URI)
     * @param headers Optional HTTP headers (e.g. Referer, User-Agent) to pass to mpv
     */
    fun loadEpisode(url: String, headers: Map<String, String>? = null) {
        val handle = mpvHandle ?: run {
            logger.warn { "🎬 VIDEO_LOAD: Cannot load episode: mpv not initialized" }
            _playbackState.value = PlaybackState.ERROR
            CrashReporter.logEvent("Video load failed", "mpv not initialized, url=$url")
            return
        }

        currentUrl = url
        _playbackState.value = PlaybackState.LOADING
        logger.info { "🎬 VIDEO_LOAD: loading episode into mpv: $url" }
        CrashReporter.logEvent("Video loading", "url=$url")

        try {
            // Set HTTP headers BEFORE loadfile so mpv sends them with the initial request.
            // mpv's http-header-fields format: "Header1: value1", "Header2: value2"
            if (!headers.isNullOrEmpty()) {
                val httpHeaderFields = headers.entries.joinToString(",") { (name, value) ->
                    val escapedValue = value.replace("\"", "\\\"")
                    "\"$name: $escapedValue\""
                }
                logger.info { "🎬 VIDEO_LOAD: setting http-header-fields: $httpHeaderFields" }
                val headerResult = MPVLib.setOptionString(handle, "http-header-fields", httpHeaderFields)
                if (headerResult != null && headerResult < 0) {
                    logger.warn { "🎬 VIDEO_LOAD: http-header-fields returned $headerResult (non-fatal)" }
                }
            }

            MPVLib.command(handle, "loadfile", url, "replace")
            logger.info { "🎬 VIDEO_LOAD: loadfile command sent successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load episode: $url" }
            CrashReporter.logError("VideoLoad", "Failed to load $url", e)
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
     * Set volume (0–200). Always updates local state even without mpv.
     */
    fun setVolume(vol: Int) {
        val clamped = vol.coerceIn(0, 200)
        _volume.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyInt(handle, "volume", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set volume on mpv" }
            }
        }
    }

    /**
     * Set playback speed (0.25–4.0). Always updates local state even without mpv.
     */
    fun setSpeed(speed: Double) {
        val clamped = speed.coerceIn(0.25, 4.0)
        _playbackSpeed.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "speed", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set speed on mpv" }
            }
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

    private val _subtitleDelay = MutableStateFlow(0.0)
    val subtitleDelay: StateFlow<Double> = _subtitleDelay.asStateFlow()

    private val _audioDelay = MutableStateFlow(0.0)
    val audioDelay: StateFlow<Double> = _audioDelay.asStateFlow()

    // -------------------------------------------------------------------------
    // Aspect ratio & video filters
    // -------------------------------------------------------------------------

    private val _aspectRatio = MutableStateFlow("-1")
    val aspectRatio: StateFlow<String> = _aspectRatio.asStateFlow()

    private val _videoRotation = MutableStateFlow(0)
    val videoRotation: StateFlow<Int> = _videoRotation.asStateFlow()

    private val _isHflip = MutableStateFlow(false)
    val isHflip: StateFlow<Boolean> = _isHflip.asStateFlow()

    private val _isVflip = MutableStateFlow(false)
    val isVflip: StateFlow<Boolean> = _isVflip.asStateFlow()

    // -------------------------------------------------------------------------
    // Video equalizer controls
    // -------------------------------------------------------------------------

    /**
     * Set video brightness.
     * UI range: -1.0..1.0 (default 0). Mapped to mpv range: -100..100.
     */
    fun setBrightness(value: Float) {
        val clamped = value.coerceIn(-1f, 1f)
        _brightness.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "brightness", (clamped * 100).toDouble())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set brightness" }
            }
        }
    }

    /**
     * Set video contrast.
     * UI range: 0.0..2.0 (default 1.0). Mapped to mpv range: -100..100.
     */
    fun setContrast(value: Float) {
        val clamped = value.coerceIn(0f, 2f)
        _contrast.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "contrast", ((clamped - 1f) * 100).toDouble())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set contrast" }
            }
        }
    }

    /**
     * Set video saturation.
     * UI range: 0.0..2.0 (default 1.0). Mapped to mpv range: -100..100.
     */
    fun setSaturation(value: Float) {
        val clamped = value.coerceIn(0f, 2f)
        _saturation.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "saturation", ((clamped - 1f) * 100).toDouble())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set saturation" }
            }
        }
    }

    /**
     * Set video gamma.
     * UI range: 0.1..2.0 (default 1.0). Mapped to mpv range: -100..100.
     */
    fun setGamma(value: Float) {
        val clamped = value.coerceIn(0.1f, 2f)
        _gamma.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "gamma", ((clamped - 1f) * 100).toDouble())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set gamma" }
            }
        }
    }

    /**
     * Reset all equalizer values to defaults.
     */
    fun resetEqualizer() {
        setBrightness(0f)
        setContrast(1f)
        setSaturation(1f)
        setGamma(1f)
    }

    /**
     * Set subtitle delay in seconds. Negative = earlier, positive = later.
     * UI range: -10.0..10.0 (default 0.0). Maps directly to mpv sub-delay.
     */
    fun setSubtitleDelay(delay: Double) {
        val clamped = delay.coerceIn(-10.0, 10.0)
        _subtitleDelay.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "sub-delay", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set subtitle delay" }
            }
        }
    }

    /**
     * Set audio delay in seconds. Negative = audio plays earlier, positive = later.
     * UI range: -10.0..10.0 (default 0.0). Maps directly to mpv audio-delay.
     */
    fun setAudioDelay(delay: Double) {
        val clamped = delay.coerceIn(-10.0, 10.0)
        _audioDelay.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "audio-delay", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set audio delay" }
            }
        }
    }

    /**
     * Set display aspect ratio.
     * Common values: "-1" (original), "4:3", "16:9", "16:10", "21:9", "3:2", "5:4", "1:1".
     */
    fun setAspectRatio(ratio: String) {
        _aspectRatio.value = ratio
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyString(handle, "video-aspect", ratio)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set aspect ratio" }
            }
        }
    }

    /**
     * Set video rotation in degrees (0, 90, 180, 270).
     */
    fun setVideoRotation(degrees: Int) {
        val clamped = when (degrees) {
            90 -> 90; 180 -> 180; 270 -> 270; else -> 0
        }
        _videoRotation.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyInt(handle, "video-rotate", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set video rotation" }
            }
        }
    }

    /**
     * Toggle horizontal flip.
     */
    fun toggleHflip() {
        _isHflip.value = !_isHflip.value
        applyVideoFilters()
    }

    /**
     * Toggle vertical flip.
     */
    fun toggleVflip() {
        _isVflip.value = !_isVflip.value
        applyVideoFilters()
    }

    private fun applyVideoFilters() {
        val handle = mpvHandle
        if (handle != null) {
            try {
                val filters = buildList {
                    if (_isHflip.value) add("hflip")
                    if (_isVflip.value) add("vflip")
                }
                MPVLib.setPropertyString(handle, "vf", filters.joinToString(","))
            } catch (e: Exception) {
                logger.warn(e) { "Failed to apply video filters" }
            }
        }
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
