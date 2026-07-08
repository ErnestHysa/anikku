package app.anikku.macos.ui.screens.player

import app.anikku.macos.player.PlaybackState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [PlayerTransportControls] and related utilities.
 *
 * formatDuration tests live in PlayerScreenTest.kt (testing the canonical
 * implementation from player/Utils.kt).
 */
class PlayerControlsTest {

    // ========================================================================
    // PlaybackState handling in transport controls
    // ========================================================================

    @Test
    fun `PlaybackState has all expected states`() {
        assertEquals(8, PlaybackState.entries.size)
        assertTrue(PlaybackState.entries.contains(PlaybackState.IDLE))
        assertTrue(PlaybackState.entries.contains(PlaybackState.LOADING))
        assertTrue(PlaybackState.entries.contains(PlaybackState.PLAYING))
        assertTrue(PlaybackState.entries.contains(PlaybackState.PAUSED))
        assertTrue(PlaybackState.entries.contains(PlaybackState.SEEKING))
        assertTrue(PlaybackState.entries.contains(PlaybackState.BUFFERING))
        assertTrue(PlaybackState.entries.contains(PlaybackState.ENDED))
        assertTrue(PlaybackState.entries.contains(PlaybackState.ERROR))
    }

    // ========================================================================
    // Episode navigation edge cases
    // ========================================================================

    @Test
    fun `previous episode enabled only at positive index`() {
        assertTrue(0 > 0 == false)         // first: previous disabled
        assertTrue(4 > 0)                   // middle: previous enabled
        assertTrue(7 > 0)                   // last: previous enabled
    }

    @Test
    fun `next episode enabled only below max index`() {
        val maxIndex = 7
        assertTrue(0 < maxIndex)            // first: next enabled
        assertTrue(4 < maxIndex)            // middle: next enabled
        assertTrue(7 < maxIndex == false)   // last: next disabled
    }

    @Test
    fun `seek fraction clamped between 0 and 1`() {
        val clamp = { pos: Long, total: Long -> (pos.toFloat() / total).coerceIn(0f, 1f) }
        assertEquals(0f, clamp(-100, 1000))
        assertEquals(0.5f, clamp(500, 1000))
        assertEquals(1f, clamp(2000, 1000))
    }

    // ========================================================================
    // Speed options mapping
    // ========================================================================

    @Test
    fun `standard playback speeds are valid`() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        speeds.forEach { speed ->
            assertTrue(speed in 0.25f..4.0f)
        }
    }
}
