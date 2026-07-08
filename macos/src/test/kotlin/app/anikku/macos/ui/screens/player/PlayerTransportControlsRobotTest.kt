package app.anikku.macos.ui.screens.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.anikku.macos.player.PlaybackState
import org.junit.Rule
import org.junit.Test

/**
 * Robot-pattern UI tests for [PlayerTransportControls].
 *
 * Uses Compose Desktop headless testing via [createComposeRule].
 * Each test renders the composable in a headless scene and interacts
 * through semantic node matchers (robot pattern).
 *
 * Uses JUnit 4 annotations for Compose Rule compatibility;
 * JUnit Vintage engine discovers these alongside JUnit 5 tests.
 */
class PlayerTransportControlsRobotTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ========================================================================
    // Play / Pause toggle
    // ========================================================================

    @Test
    fun `play button displays when paused and toggles on click`() {
        var toggled = false
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = { toggled = true },
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").performClick()
        assert(toggled) { "Play button should invoke onTogglePlay" }
    }

    @Test
    fun `pause button displays when playing and toggles on click`() {
        var toggled = false
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = true,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = { toggled = true },
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        assert(toggled) { "Pause button should invoke onTogglePlay" }
    }

    // ========================================================================
    // Episode navigation
    // ========================================================================

    @Test
    fun `previous episode button navigates backward`() {
        var lastIndex = -1
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 3,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = { lastIndex = it },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Previous episode").performClick()
        assert(lastIndex == 2) { "Previous episode should navigate to index 2, got $lastIndex" }
    }

    @Test
    fun `next episode button navigates forward`() {
        var lastIndex = -1
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 3,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = { lastIndex = it },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Next episode").performClick()
        assert(lastIndex == 4) { "Next episode should navigate to index 4, got $lastIndex" }
    }

    @Test
    fun `previous episode button is disabled at first episode`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Previous episode").assertIsNotEnabled()
    }

    @Test
    fun `next episode button is disabled at last episode`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 4,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Next episode").assertIsNotEnabled()
    }

    @Test
    fun `previous episode button is enabled in middle`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 2,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Previous episode").assertIsEnabled()
    }

    @Test
    fun `next episode button is enabled in middle`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 2,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Next episode").assertIsEnabled()
    }

    // ========================================================================
    // Seek controls (rewind / forward)
    // ========================================================================

    @Test
    fun `rewind button seeks backward 10 seconds`() {
        var offset = 0.0
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = { offset = it },
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Rewind 10 seconds").performClick()
        assert(offset == -10.0) { "Rewind should seek by -10.0, got $offset" }
    }

    @Test
    fun `forward button seeks forward 10 seconds`() {
        var offset = 0.0
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = { offset = it },
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Forward 10 seconds").performClick()
        assert(offset == 10.0) { "Forward should seek by 10.0, got $offset" }
    }

    // ========================================================================
    // Time display
    // ========================================================================

    @Test
    fun `elapsed time displays current position`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 65,
                    totalDurationSeconds = 3600,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("01:05").assertIsDisplayed()
    }

    @Test
    fun `total duration displays total time`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 3600,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("1:00:00").assertIsDisplayed()
    }

    @Test
    fun `time labels show correct formatting for short durations`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 90,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("00:00").assertIsDisplayed()
        composeRule.onNodeWithText("01:30").assertIsDisplayed()
    }

    // ========================================================================
    // Volume display
    // ========================================================================

    @Test
    fun `volume indicator shown when showVolume is true`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    volume = 75,
                    showVolume = true,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("Volume: 75").assertIsDisplayed()
    }

    @Test
    fun `volume indicator hidden when showVolume is false`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    volume = 75,
                    showVolume = false,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("Volume: 75").assertDoesNotExist()
    }

    // ========================================================================
    // Playback status text
    // ========================================================================

    @Test
    fun `shows loading status when playbackState is LOADING`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    playbackState = PlaybackState.LOADING,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("Loading").assertIsDisplayed()
    }

    @Test
    fun `shows buffering status when playbackState is BUFFERING`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    playbackState = PlaybackState.BUFFERING,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("Buffering").assertIsDisplayed()
    }

    @Test
    fun `shows error status when playbackState is ERROR`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    playbackState = PlaybackState.ERROR,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("Error").assertIsDisplayed()
    }

    @Test
    fun `shows ended status when playbackState is ENDED`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    playbackState = PlaybackState.ENDED,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("Ended").assertIsDisplayed()
    }

    @Test
    fun `shows paused status when playbackState is PAUSED`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    playbackState = PlaybackState.PAUSED,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("Paused").assertIsDisplayed()
    }

    @Test
    fun `hides status text when playbackState is IDLE`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    playbackState = PlaybackState.IDLE,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("Loading").assertDoesNotExist()
        composeRule.onNodeWithText("Buffering").assertDoesNotExist()
        composeRule.onNodeWithText("Error").assertDoesNotExist()
        composeRule.onNodeWithText("Ended").assertDoesNotExist()
        composeRule.onNodeWithText("Paused").assertDoesNotExist()
    }

    @Test
    fun `hides status text when playbackState is PLAYING`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = true,
                    playbackState = PlaybackState.PLAYING,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithText("Loading").assertDoesNotExist()
        composeRule.onNodeWithText("Paused").assertDoesNotExist()
    }

    // ========================================================================
    // All transport buttons are present
    // ========================================================================

    @Test
    fun `all transport buttons render with expected descriptions`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 2,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Previous episode").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rewind 10 seconds").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Forward 10 seconds").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next episode").assertIsDisplayed()
    }

    @Test
    fun `play button renders when paused`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause").assertDoesNotExist()
    }

    @Test
    fun `pause button renders when playing`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = true,
                    currentEpisodeIndex = 0,
                    episodeCount = 5,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    // ========================================================================
    // TransportIconButton — direct composable test
    // ========================================================================

    @Test
    fun `TransportIconButton invokes onClick when enabled`() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                TransportIconButton(
                    icon = Icons.Outlined.PlayCircle,
                    description = "Custom test button",
                    enabled = true,
                    onClick = { clicked = true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Custom test button").performClick()
        assert(clicked) { "TransportIconButton should invoke onClick" }
    }

    @Test
    fun `TransportIconButton is disabled when enabled is false`() {
        composeRule.setContent {
            MaterialTheme {
                TransportIconButton(
                    icon = Icons.Outlined.PlayCircle,
                    description = "Custom test button",
                    enabled = false,
                    onClick = { },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Custom test button").assertIsNotEnabled()
    }

    // ========================================================================
    // Edge cases: single episode
    // ========================================================================

    @Test
    fun `both prev and next disabled with single episode`() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTransportControls(
                    currentPositionSeconds = 0,
                    totalDurationSeconds = 100,
                    isPlaying = false,
                    currentEpisodeIndex = 0,
                    episodeCount = 1,
                    onTogglePlay = {},
                    onSeek = {},
                    onSeekEnd = {},
                    onSeekRelative = {},
                    onNavigateEpisode = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Previous episode").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next episode").assertIsNotEnabled()
    }
}
