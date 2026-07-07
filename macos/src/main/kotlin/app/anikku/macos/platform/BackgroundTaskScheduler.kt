package app.anikku.macos.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Replaces Android WorkManager for background tasks on macOS desktop.
 *
 * Key difference from Android: on macOS, there is no guaranteed background execution
 * when the app is closed. Tasks run only while the app is open, which matches user
 * expectations for a desktop app.
 *
 * Usage:
 * ```
 * val scheduler = BackgroundTaskScheduler(applicationScope)
 * scheduler.schedulePeriodic("library-update", intervalMinutes = 60) {
 *     updateLibrary()
 * }
 * scheduler.runOnce("backup") {
 *     createBackup()
 * }
 * ```
 */
class BackgroundTaskScheduler(
    private val scope: CoroutineScope,
) {

    private val runningTasks = mutableMapOf<String, Job>()

    /**
     * Schedules a periodic task that runs at the given interval.
     * Runs once immediately, then repeats after each interval.
     */
    fun schedulePeriodic(
        name: String,
        intervalMinutes: Long,
        runImmediately: Boolean = false,
        task: suspend () -> Unit,
    ): Job {
        cancelTask(name)

        return scope.launch {
            if (runImmediately) {
                runCatching { task() }
            }
            while (isActive) {
                delay(intervalMinutes.minutes)
                runCatching { task() }
            }
        }.also {
            runningTasks[name] = it
        }
    }

    /**
     * Schedules a periodic task with a Duration interval.
     */
    fun schedulePeriodic(
        name: String,
        interval: Duration,
        runImmediately: Boolean = false,
        task: suspend () -> Unit,
    ): Job {
        return scope.launch {
            if (runImmediately) {
                runCatching { task() }
            }
            while (isActive) {
                delay(interval)
                runCatching { task() }
            }
        }.also {
            runningTasks[name] = it
        }
    }

    /**
     * Runs a one-shot task and returns its Job.
     */
    fun runOnce(
        name: String,
        task: suspend () -> Unit,
    ): Job {
        return scope.launch {
            runCatching { task() }
            runningTasks.remove(name)
        }.also {
            runningTasks[name] = it
        }
    }

    /**
     * Cancels a running task by name.
     */
    fun cancelTask(name: String) {
        runningTasks[name]?.cancel()
        runningTasks.remove(name)
    }

    /**
     * Cancels all running tasks.
     */
    fun cancelAll() {
        runningTasks.values.forEach { it.cancel() }
        runningTasks.clear()
    }

    /**
     * Returns true if a task with the given name is currently scheduled.
     */
    fun isRunning(name: String): Boolean {
        return runningTasks[name]?.isActive == true
    }
}
