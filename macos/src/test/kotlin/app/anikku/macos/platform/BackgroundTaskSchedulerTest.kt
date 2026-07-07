package app.anikku.macos.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class BackgroundTaskSchedulerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var scheduler: BackgroundTaskScheduler

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scheduler = BackgroundTaskScheduler(scope)
    }

    @AfterEach
    fun tearDown() {
        scheduler.cancelAll()
        scope.cancel()
    }

    @Test
    fun `runOnce executes the task`() = runBlocking {
        var executed = false
        scheduler.runOnce("test-task") {
            executed = true
        }
        delay(200) // real delay — scheduler runs on Dispatchers.Default
        assertTrue(executed)
    }

    @Test
    fun `isRunning returns true for active task`() = runBlocking {
        scheduler.runOnce("test-task") {
            delay(10000)
        }
        delay(100)
        assertTrue(scheduler.isRunning("test-task"))
    }

    @Test
    fun `isRunning returns false for completed task`() = runBlocking {
        scheduler.runOnce("quick-task") {
            // immediate completion
        }
        delay(200)
        assertFalse(scheduler.isRunning("quick-task"))
    }

    @Test
    fun `cancelTask stops a running task`() = runBlocking {
        var executed = false
        scheduler.runOnce("cancel-me") {
            delay(5000)
            executed = true
        }
        delay(100)
        scheduler.cancelTask("cancel-me")
        delay(200)
        assertFalse(executed)
        assertFalse(scheduler.isRunning("cancel-me"))
    }

    @Test
    fun `cancelAll stops all tasks`() = runBlocking {
        var executed1 = false
        var executed2 = false

        scheduler.runOnce("task1") {
            delay(5000)
            executed1 = true
        }
        scheduler.runOnce("task2") {
            delay(5000)
            executed2 = true
        }

        delay(100)
        scheduler.cancelAll()

        delay(200)
        assertFalse(executed1)
        assertFalse(executed2)
        assertFalse(scheduler.isRunning("task1"))
        assertFalse(scheduler.isRunning("task2"))
    }

    @Test
    fun `schedulePeriodic runs repeatedly`() = runBlocking {
        val counter = MutableStateFlow(0)

        scheduler.schedulePeriodic(
            name = "counter",
            interval = 100.milliseconds,
            runImmediately = true,
        ) {
            counter.value++
        }

        delay(350) // allow 3-4 executions (immediate + 3 after 100ms intervals)
        scheduler.cancelTask("counter")
        delay(50) // wait for cancellation to take effect

        assertTrue(counter.value >= 2) {
            "Expected counter >= 2, got ${counter.value}"
        }
    }

    @Test
    fun `exception in task does not crash scheduler`() = runBlocking {
        var afterError = false

        scheduler.runOnce("failing-task") {
            throw RuntimeException("Task failed!")
        }

        scheduler.runOnce("second-task") {
            afterError = true
        }

        delay(200)
        assertTrue(afterError)
        assertFalse(scheduler.isRunning("failing-task"))
    }
}
