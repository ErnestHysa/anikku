package app.anikku.macos.platform

import org.junit.jupiter.api.Test

class MacOSDockManagerTest {

    @Test
    fun `set badge count does not throw`() {
        // In test environment, Dock bridge may not be available
        // But calling it should not throw
        MacOSDockManager.setBadgeCount(5)
        assert(true)
    }

    @Test
    fun `clear badge does not throw`() {
        MacOSDockManager.clearBadge()
        assert(true)
    }

    @Test
    fun `request user attention does not throw`() {
        MacOSDockManager.requestUserAttention(critical = false)
        assert(true)
    }

    @Test
    fun `critical user attention does not throw`() {
        MacOSDockManager.requestUserAttention(critical = true)
        assert(true)
    }

    @Test
    fun `set play pause menu action does not throw`() {
        MacOSDockManager.setPlayPauseMenuAction { /* no-op */ }
        assert(true)
    }

    @Test
    fun `set badge count to zero is safe`() {
        MacOSDockManager.setBadgeCount(0)
        assert(true)
    }

    @Test
    fun `set badge count to negative is safe`() {
        MacOSDockManager.setBadgeCount(-1)
        assert(true)
    }
}
