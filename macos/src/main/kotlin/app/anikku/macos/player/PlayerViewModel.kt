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
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Central player state and control model.
 *
 * Manages playback lifecycle, position tracking, audio/subtitle tracks,
 * and episode navigation. Communicates with mpv via [MPVLib] and
 * processes events via [MPVEventLoop].
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

    /** Magnet streamer process when playing a torrent. */
    private var magnetProcess: Process? = null

    /**
     * Token to guard against stale magnet-load coroutines firing after
     * the user switches to a different episode.
     */
    @Volatile
    private var magnetLoadToken: Any? = null

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

            // Set network timeout so mpv doesn't hang forever on unreachable servers
            setNetworkTimeout(handle)

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
                            val eventToken = loadToken
                            if (loadToken === eventToken) {
                                _playbackState.value = PlaybackState.PLAYING
                                _isPaused.value = false
                                updateDuration()
                                loadTimeoutJob?.cancel()
                                loadTimeoutJob = null
                                logger.info { "🎬 VIDEO_FILE: file loaded into mpv — starting playback" }
                            } else {
                                logger.debug { "🎬 VIDEO_FILE: ignoring stale FILE_LOADED from previous load" }
                            }
                        }
                        MPVLib.MPV_EVENT_END_FILE -> {
                            val eventToken = loadToken
                            if (_playbackState.value == PlaybackState.LOADING ||
                                _playbackState.value == PlaybackState.SEEKING) {
                                if (loadToken === eventToken) {
                                    _playbackState.value = PlaybackState.ERROR
                                    logger.warn { "🎬 VIDEO_END: file ended while still loading — likely unreachable URL, blocked CDN, or embed page URL" }
                                } else {
                                    logger.debug { "🎬 VIDEO_END: ignoring stale END_FILE from previous load (state was LOADING/SEEKING)" }
                                }
                            } else if (_playbackState.value == PlaybackState.PLAYING) {
                                // File ended during normal playback. With keep-open=no,
                                // mpv stops playback entirely. We keep the PLAYING state
                                // so the UI shows the last frame with working controls.
                                if (loadToken === eventToken) {
                                    logger.info { "🎬 VIDEO_FILE: playback ended (keep-open=no, stream finished)" }
                                } else {
                                    logger.debug { "🎬 VIDEO_END: ignoring stale END_FILE from previous load" }
                                }
                            } else {
                                if (loadToken === eventToken) {
                                    _playbackState.value = PlaybackState.ENDED
                                    logger.info { "🎬 VIDEO_FILE: playback ended normally" }
                                } else {
                                    logger.debug { "🎬 VIDEO_END: ignoring stale END_FILE from previous load" }
                                }
                            }

                            if (loadToken === eventToken) {
                                loadTimeoutJob?.cancel()
                                loadTimeoutJob = null
                            }
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
                            val eventToken = loadToken
                            if (loadToken === eventToken) {
                                _playbackState.value = PlaybackState.PLAYING
                                _isPaused.value = false
                                loadTimeoutJob?.cancel()
                                loadTimeoutJob = null
                                logger.info { "🎬 VIDEO_RESTART: playback restarted after seek/load" }
                            } else {
                                logger.debug { "🎬 VIDEO_RESTART: ignoring stale PLAYBACK_RESTART from previous load" }
                            }
                        }
                        MPVLib.MPV_EVENT_SEEK -> {
                            _playbackState.value = PlaybackState.SEEKING
                            logger.info { "🎬 VIDEO_SEEK: mpv seek event — position=${currentPosition.value}" }
                        }
                        MPVLib.MPV_EVENT_SHUTDOWN -> {
                            _playbackState.value = PlaybackState.IDLE
                        }
                    }
                }
            }

            // Periodic position updates — always update regardless of playback state
            positionUpdateJob = scope.launch {
                while (isActive) {
                    delay(250) // 250ms for smoother position tracking
                    if (mpvHandle != null) {
                        updatePosition()
                        updateDuration()
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
     */
    private fun configureMPV(handle: Pointer): Boolean {
        var allOk = true
        val criticalOptions = listOf(
            "vo" to "libmpv",
            "hwdec" to "videotoolbox-copy",
            "cache" to "yes",
            "cache-secs" to "30",
            "demuxer-max-bytes" to "150M",
            "demuxer-max-back-bytes" to "50M",
        )
        val nonCriticalOptions = listOf(
            "audio-file-auto" to "no",
            "ytdl" to "yes",
            "ytdl-format" to "bestvideo+bestaudio/best",
            "ytdl-raw-options" to "format-sort=+size:codec:h264:avc1:res:br,fragment-retries=10",
            "sub-auto" to "fuzzy",
            "sub-file-auto" to "no",
            "osd-level" to "0",
            // Use keep-open=no to prevent mpv from staying on the last frame and
            // instead allow proper cleanup and state transitions.
            "keep-open" to "no",
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
     * Set a network timeout on mpv so it doesn't hang forever when a server
     * is unreachable, blocked by Cloudflare, or the URL is not a valid stream.
     */
    private fun setNetworkTimeout(handle: Pointer) {
        try {
            // stream-timeout: seconds to wait for network I/O (default = 0 = infinite)
            // Use 30s — long enough for slow CDNs but short enough to avoid hanging.
            val result = MPVLib.setOptionString(handle, "stream-timeout", "30")
            if (result != null && result < 0) {
                logger.debug { "stream-timeout option not supported by this mpv build (non-fatal)" }
            } else {
                logger.info { "🎬 MPV_CONFIG: stream-timeout set to 30s" }
            }

            val result3 = MPVLib.setOptionString(handle, "demuxer-readahead-secs", "20")
            if (result3 != null && result3 < 0) {
                logger.debug { "demuxer-readahead-secs option not supported (non-fatal)" }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to set network timeout options (non-fatal)" }
        }
    }

    /**
     * Shut down the player, clean up mpv resources.
     */
    fun shutdown() {
        logger.info { "🎬 PLAYER_SHUTDOWN: shutting down mpv player..." }
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
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

        // Clean up magnet streamer process if running
        magnetProcess?.let { proc ->
            try {
                MagnetStreamer.stopStreaming(MagnetStreamResult.Success("", proc))
            } catch (_: Exception) { }
            magnetProcess = null
            logger.info { "🧲 MAGNET_SHUTDOWN: torrent stream process terminated" }
        }

        _playbackState.value = PlaybackState.IDLE
        logger.info { "🎬 PLAYER_SHUTDOWN: mpv player shut down complete" }
        CrashReporter.logEvent("Player shutdown")
    }

    // -------------------------------------------------------------------------
    // Playback Controls
    // -------------------------------------------------------------------------

    /** Tracks the load timeout coroutine so it can be cancelled on success/error. */
    private var loadTimeoutJob: Job? = null

    @Volatile
    private var loadToken: Any? = null

    /**
     * Load and play a video URL with optional HTTP headers.
     */
    fun loadEpisode(url: String, headers: Map<String, String>? = null) {
        // ── Magnet link handling ────────────────────────────────────
        if (url.startsWith("magnet:")) {
            loadMagnetEpisode(url)
            return
        }

        val handle = mpvHandle ?: run {
            logger.warn { "🎬 VIDEO_LOAD: Cannot load episode: mpv not initialized" }
            _playbackState.value = PlaybackState.ERROR
            CrashReporter.logEvent("Video load failed", "mpv not initialized, url=$url")
            return
        }

        currentUrl = url

        val token = Any()
        loadToken = token

        _playbackState.value = PlaybackState.LOADING
        logger.info { "🎬 VIDEO_LOAD: loading episode into mpv: $url" }
        CrashReporter.logEvent("Video loading", "url=$url")

        try {
            val userAgent = headers?.entries?.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                ?: MPVLib.DEFAULT_USER_AGENT

            if (headers.isNullOrEmpty()) {
                val clearResult = MPVLib.setPropertyString(handle, "http-header-fields", "")
                if (clearResult != null && clearResult < 0) {
                    logger.debug { "Failed to clear http-header-fields (error $clearResult) — non-fatal" }
                }
            } else {
                val httpHeaderFields = headers.entries
                    .filter { !it.key.equals("User-Agent", ignoreCase = true) }
                    .joinToString(",") { (name, value) ->
                        val escapedValue = value.replace(",", "\\,")
                        "$name: $escapedValue"
                    }
                val headerResult = MPVLib.setPropertyString(handle, "http-header-fields", httpHeaderFields)
                if (headerResult != null && headerResult < 0) {
                    logger.warn { "Failed to set http-header-fields (error $headerResult) — headers may not be sent" }
                }
            }
            val uaResult = MPVLib.setPropertyString(handle, "user-agent", userAgent)
            if (uaResult != null && uaResult < 0) {
                logger.warn { "Failed to set user-agent property (error $uaResult) — User-Agent may not be sent" }
            }

            // Dynamically set ytdl-format based on URL type
            val ytdlFormat = if (url.contains(".mpd", ignoreCase = true) ||
                url.contains("dash", ignoreCase = true) ||
                url.contains("manifest", ignoreCase = true)
            ) {
                "bestvideo+bestaudio/best"
            } else {
                "best"
            }
            val formatResult = MPVLib.setPropertyString(handle, "ytdl-format", ytdlFormat)
            if (formatResult != null && formatResult < 0) {
                logger.debug { "Failed to set ytdl-format=$ytdlFormat (error $formatResult) — non-fatal" }
            } else if (ytdlFormat != "best") {
                logger.info { "🎬 VIDEO_LOAD: set ytdl-format=$ytdlFormat for DASH stream" }
            }

            logger.info { "🎬 VIDEO_LOAD: set http-header-fields and user-agent properties" }
            MPVLib.command(handle, "loadfile", url, "replace")
            logger.info { "🎬 VIDEO_LOAD: loadfile command sent successfully" }

            // Start a timeout: if the file doesn't load within 30 seconds,
            // transition to ERROR to prevent getting stuck at LOADING.
            loadTimeoutJob?.cancel()
            loadTimeoutJob = scope.launch {
                delay(LOAD_TIMEOUT_MS)
                if (loadToken !== token) return@launch

                val state = _playbackState.value
                if (state == PlaybackState.LOADING || state == PlaybackState.SEEKING) {
                    _playbackState.value = PlaybackState.ERROR
                    logger.warn { "🎬 VIDEO_TIMEOUT: file did not load within ${LOAD_TIMEOUT_MS / 1000}s" +
                        " — server may be unreachable, blocked, or URL not a playable stream" }
                    CrashReporter.logEvent("Video timeout", "url=$url, state=$state" )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load episode: $url" }
            CrashReporter.logError("VideoLoad", "Failed to load $url", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }

    // ── Magnet Link Handling ──────────────────────────────────────

    private fun loadMagnetEpisode(magnetUrl: String) {
        logger.info { "🧲 MAGNET: Starting torrent stream: ${magnetUrl.take(80)}..." }
        _playbackState.value = PlaybackState.LOADING

        val token = Any()
        magnetLoadToken = token

        magnetProcess?.let { prevProcess ->
            try {
                prevProcess.destroy()
                prevProcess.waitFor(3, TimeUnit.SECONDS)
                MagnetStreamer.stopStreaming(MagnetStreamResult.Success("", prevProcess))
            } catch (_: Exception) { }
            magnetProcess = null
        }

        scope.launch {
            try {
                val result = MagnetStreamer.startStreaming(magnetUrl)
                if (magnetLoadToken !== token) return@launch

                withContext(Dispatchers.Main) {
                    when (result) {
                        is MagnetStreamResult.Success -> {
                            magnetProcess = result.process
                            logger.info { "🧲 MAGNET: webtorrent server ready at ${result.httpUrl}" }
                            if (magnetLoadToken !== token) {
                                MagnetStreamer.stopStreaming(result)
                                return@withContext
                            }
                            loadEpisode(result.httpUrl)
                        }
                        is MagnetStreamResult.Failure -> {
                            if (magnetLoadToken !== token) return@withContext
                            _playbackState.value = PlaybackState.ERROR
                            logger.warn { "🧲 MAGNET: Failed to start torrent stream: ${result.message}" }
                            CrashReporter.logEvent("Magnet stream failed", result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                if (magnetLoadToken !== token) return@launch
                withContext(Dispatchers.Main) {
                    _playbackState.value = PlaybackState.ERROR
                    logger.error(e) { "🧲 MAGNET: Unexpected error streaming torrent" }
                    CrashReporter.logError("MagnetError", e.message ?: "", e)
                }
            }
        }
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 30_000L // 30 seconds for slower CDNs
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
     */
    fun seekRelative(offset: Double) {
        seekTo(currentPosition.value + offset)
    }

    /**
     * Set volume (0–200).
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
     * Set playback speed (0.25–4.0).
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
            _isFullscreen.value = !_isFullscreen.value
        }
    }

    /**
     * Take a screenshot of the current video frame.
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
        val trackList = MPVLib.getPropertyString(handle, "track-list") ?: return

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

    fun resetEqualizer() {
        setBrightness(0f)
        setContrast(1f)
        setSaturation(1f)
        setGamma(1f)
    }

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

    fun toggleHflip() {
        _isHflip.value = !_isHflip.value
        applyVideoFilters()
    }

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
            if (dur > 0) {
                _duration.value = dur
            }
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
