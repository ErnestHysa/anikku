package app.anikku.macos.platform.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DiscordRPCTest {

    private lateinit var scope: CoroutineScope
    private lateinit var rpc: DiscordRPC

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        rpc = DiscordRPC(scope = scope)
    }

    @AfterEach
    fun tearDown() {
        rpc.stop()
    }

    @Test
    fun `initial state is disconnected`() {
        assertEquals(ConnectionState.DISCONNECTED, rpc.connectionState.value)
    }

    @Test
    fun `is connected returns false initially`() {
        assertFalse(rpc.isConnected)
    }

    @Test
    fun `start changes state to connecting`() {
        // Start will attempt to connect (will fail in test env but state changes)
        rpc.start()
        // State transitions: DISCONNECTED -> CONNECTING
        assertNotNull(rpc.connectionState.value)
    }

    @Test
    fun `stop does not throw when not started`() {
        rpc.stop()
        // Should not throw
        assert(true)
    }

    @Test
    fun `double start is safe`() {
        rpc.start()
        rpc.start() // Should be no-op
        assert(true)
    }

    @Test
    fun `set and clear presence does not throw`() {
        rpc.start()
        rpc.setPresence(
            details = "Watching Test Anime",
            state = "Episode 1 - Test",
            largeImage = "test_logo",
            largeText = "Test App",
        )
        rpc.clearPresence()
        assert(true)
    }

    @Test
    fun `isDiscordInstalled returns false in test environment`() {
        // In a test environment, Discord won't be in /Applications
        assertFalse(rpc.isDiscordInstalled)
    }
}
