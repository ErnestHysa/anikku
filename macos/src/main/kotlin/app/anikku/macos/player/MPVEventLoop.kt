package app.anikku.macos.player

import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Dedicated coroutine-based event loop for processing mpv events.
 *
 * Polls mpv's event queue in a background coroutine and emits events
 * to subscribers via [events] Flow.
 *
 * ## Usage
 *
 * ```kotlin
 * val eventLoop = MPVEventLoop(mpvHandle)
 * eventLoop.start(scope)
 *
 * // Observe events
 * eventLoop.events.collect { event ->
 *     when (event.eventId) {
 *         MPVLib.MPV_EVENT_FILE_LOADED -> onFileLoaded()
 *         MPVLib.MPV_EVENT_PLAYBACK_RESTART -> onPlaybackStarted()
 *         MPVLib.MPV_EVENT_PROPERTY_CHANGE -> onPropertyChanged(event)
 *     }
 * }
 * ```
 *
 * ## Property observation
 *
 * Before starting the loop, register properties to observe:
 * ```kotlin
 * eventLoop.observeProperty("time-pos")
 * eventLoop.observeProperty("duration")
 * eventLoop.observeProperty("pause")
 * ```
 */
class MPVEventLoop(
    private val mpvHandle: Pointer,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var eventJob: Job? = null

    private val _events = MutableSharedFlow<MPVEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _propertyChanges = MutableSharedFlow<PropertyChange>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** All mpv events emitted as a hot Flow. */
    val events: Flow<MPVEvent> = _events.asSharedFlow()

    /** Property changes (from observed properties) emitted as a hot Flow. */
    val propertyChanges: Flow<PropertyChange> = _propertyChanges.asSharedFlow()

    /** Whether the event loop is currently running. */
    var isRunning: Boolean = false
        private set

    /**
     * Start the event loop. Observes the registered properties and
     * begins polling for events.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        eventJob = scope.launch {
            logger.info { "MPV event loop started" }
            while (isActive) {
                try {
                    val event = MPVLib.waitEvent(mpvHandle, 0.05) // 50ms timeout
                    if (event != null) {
                        processEvent(event)
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        logger.warn(e) { "Error in mpv event loop" }
                    }
                }
            }
            logger.info { "MPV event loop stopped" }
        }
    }

    /**
     * Stop the event loop and clean up.
     */
    fun stop() {
        isRunning = false
        eventJob?.cancel()
        eventJob = null
    }

    /**
     * Observe a property for changes.
     * @param name The mpv property name (e.g. "time-pos", "duration", "pause")
     * @param format The mpv format constant (default: FORMAT_DOUBLE)
     */
    fun observeProperty(name: String, format: Int = MPVLib.FORMAT_DOUBLE) {
        try {
            MPVLib.observeProperty(mpvHandle, name.hashCode().toLong(), name, format)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to observe property: $name" }
        }
    }

    /**
     * Stop observing a property.
     */
    fun unobserveProperty(name: String) {
        try {
            MPVLib.unobserveProperty(mpvHandle, name.hashCode().toLong())
        } catch (e: Exception) {
            // Safe to ignore
        }
    }

    private fun processEvent(event: MPVEvent) {
        _events.tryEmit(event)

        when (event.eventId) {
            MPVLib.MPV_EVENT_PROPERTY_CHANGE -> {
                processPropertyChange(event)
            }
            MPVLib.MPV_EVENT_SHUTDOWN -> {
                logger.info { "MPV shutdown event received" }
                isRunning = false
            }
            MPVLib.MPV_EVENT_LOG_MESSAGE -> {
                // Log messages can be noisy — only log at debug level
            }
        }
    }

    private fun processPropertyChange(event: MPVEvent) {
        // Parse property change event data
        // The mpv_event_property struct has:
        //   name: *const c_char
        //   format: mpv_format
        //   data: *mut c_void
        val dataPtr = event.data
        if (dataPtr == null || dataPtr == Pointer.NULL) return

        try {
            // Read property name at offset 0 (pointer to null-terminated string)
            val namePtr = dataPtr.getPointer(0)
            val name = namePtr?.getString(0) ?: return

            // Read format at offset 8 (on 64-bit: pointer + long = 8 + 8 = 16)
            val format = dataPtr.getInt(8)

            _propertyChanges.tryEmit(PropertyChange(name, format, event.replyUserdata))
        } catch (e: Exception) {
            logger.debug { "Failed to parse property change event: ${e.message}" }
        }
    }
}

/**
 * Represents a property change event from mpv.
 */
data class PropertyChange(
    val name: String,
    val format: Int,
    val replyUserdata: Long,
)
