package app.anikku.macos.platform.logging

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TerminalErrorLoggerTest {

    @BeforeEach
    fun setup() {
        TerminalErrorLogger.clear()
    }

    @AfterEach
    fun tearDown() {
        TerminalErrorLogger.clear()
    }

    @Test
    fun `logUiError stores error with sequential id`() {
        TerminalErrorLogger.logUiError("Something failed")
        TerminalErrorLogger.logUiError("Another failure")

        val errors = TerminalErrorLogger.errors
        assertEquals(2, errors.size)
        assertEquals(1L, errors[0].id)
        assertEquals(2L, errors[1].id)
    }

    @Test
    fun `logUiError captures source and location`() {
        val exception = RuntimeException("Boom")
        TerminalErrorLogger.logUiError(
            message = "Extension failed",
            source = "TestExtension",
            throwable = exception,
            location = "TestScreen.load",
        )

        val error = TerminalErrorLogger.errors.single()
        assertEquals("Extension failed", error.message)
        assertEquals("TestExtension", error.source)
        assertEquals("TestScreen.load", error.location)
        assertNotNull(error.throwable)
        assertEquals("Boom", error.throwable?.message)
        assertNotNull(error.stackTrace)
    }

    @Test
    fun `errorCount returns number of captured errors`() {
        assertEquals(0, TerminalErrorLogger.errorCount)
        TerminalErrorLogger.logUiError("One")
        TerminalErrorLogger.logUiError("Two")
        assertEquals(2, TerminalErrorLogger.errorCount)
    }

    @Test
    fun `clear removes all errors and resets id`() {
        TerminalErrorLogger.logUiError("One")
        TerminalErrorLogger.logUiError("Two")
        TerminalErrorLogger.clear()

        assertEquals(0, TerminalErrorLogger.errorCount)

        TerminalErrorLogger.logUiError("Three")
        assertEquals(1L, TerminalErrorLogger.errors.single().id)
    }

    @Test
    fun `printShutdownSummary does not throw when no errors`() {
        TerminalErrorLogger.printShutdownSummary()
        assert(true)
    }

    @Test
    fun `printShutdownSummary does not throw with errors`() {
        TerminalErrorLogger.logUiError("Failure", source = "TestSource")
        TerminalErrorLogger.printShutdownSummary()
        assert(true)
    }

    @Test
    fun `errors list is capped to avoid unbounded growth`() {
        repeat(150) { index ->
            TerminalErrorLogger.logUiError("Error $index")
        }
        // The internal cap should keep only the most recent errors.
        assertTrue(TerminalErrorLogger.errorCount <= 100)
    }

}

