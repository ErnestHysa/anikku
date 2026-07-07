package app.anikku.macos.ui.screens.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastHost
import app.anikku.macos.ui.components.ToastHostState
import app.anikku.macos.ui.theme.AnikkuTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [DownloadQueueScreen].
 *
 * Verifies rendering of the download queue header, download item cards,
 * and the toast-wired pause/resume/cancel buttons.
 *
 * Calls [DownloadQueueScreen.Content] directly since the screen does not
 * use LocalNavigator, avoiding the need for a Voyager Navigator wrapper.
 *
 * Note: Interactive click tests (performClick/performSemanticsAction) are
 * not available in Compose Multiplatform 1.11.1. Toast-wired button feedback
 * is tested indirectly by verifying [ToastHost] renders without error when
 * the [LocalToastHost] provider is wired alongside [DownloadQueueScreen].
 * Direct toast triggers are tested via [runOnIdle].
 */
class DownloadQueueScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Renders the download queue screen by calling its @Composable Content() method
     * wrapped in AnikkuTheme for proper Material theming.
     */
    @Composable
    private fun RenderDownloadQueue() {
        AnikkuTheme {
            DownloadQueueScreen().Content()
        }
    }

    /**
     * Wraps the download queue screen with the required providers for toast testing,
     * including LocalToastHost, AnikkuTheme, and a ToastHost overlay.
     */
    @Composable
    private fun FullDownloadContent(
        toastHostState: ToastHostState = ToastHostState(),
    ) {
        CompositionLocalProvider(
            LocalToastHost provides toastHostState,
        ) {
            AnikkuTheme {
                Box(Modifier.fillMaxSize()) {
                    DownloadQueueScreen().Content()
                    ToastHost(state = toastHostState)
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Header rendering
    // -----------------------------------------------------------------------

    @Test
    fun `renders Downloads header`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
    }

    @Test
    fun `renders download count summary`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        // Mock data: 2 active (downloading) · 2 completed
        composeTestRule.onNodeWithText("2 active · 2 completed").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Download item rendering
    // -----------------------------------------------------------------------

    @Test
    fun `renders downloading anime titles`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        composeTestRule.onNodeWithText("Attack on Titan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jujutsu Kaisen").assertIsDisplayed()
    }

    @Test
    fun `renders completed anime titles`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        composeTestRule.onNodeWithText("One Piece").assertIsDisplayed()
        composeTestRule.onNodeWithText("Demon Slayer").assertIsDisplayed()
    }

    @Test
    fun `renders paused anime title`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        composeTestRule.onNodeWithText("Spy x Family").assertIsDisplayed()
    }

    @Test
    fun `renders episode names`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        composeTestRule.onNodeWithText("Episode 3 - A Dim Light Amid Despair").assertIsDisplayed()
        composeTestRule.onNodeWithText("Episode 1092 - A Night to Remember").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Button rendering — pause/resume/cancel content descriptions
    // -----------------------------------------------------------------------

    @Test
    fun `renders Pause button for downloading items`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        // Items 1 and 2 are Downloading — each has a Pause icon button
        composeTestRule.onAllNodesWithContentDescription("Pause").assertCountEquals(2)
    }

    @Test
    fun `renders Resume button for paused items`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        // Item 4 (Spy x Family) is Paused — has a Resume icon button
        composeTestRule.onNodeWithContentDescription("Resume").assertIsDisplayed()
    }

    @Test
    fun `renders Cancel button for active downloads`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        // Items 1, 2 (Downloading) and 4 (Paused) have Cancel buttons
        // Item 3 and 5 (Completed) do NOT have Cancel buttons
        composeTestRule.onAllNodesWithContentDescription("Cancel").assertCountEquals(3)
    }

    @Test
    fun `renders Completed icon for completed items`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        // Items 3 (One Piece) and 5 (Demon Slayer) are Completed
        composeTestRule.onAllNodesWithContentDescription("Completed").assertCountEquals(2)
    }

    // -----------------------------------------------------------------------
    // ToastHost integration — full wired environment renders without error
    // -----------------------------------------------------------------------

    @Test
    fun `renders with ToastHost provider without crash`() {
        composeTestRule.setContent {
            FullDownloadContent()
        }

        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 active · 2 completed").assertIsDisplayed()
    }

    @Test
    fun `renders download items with ToastHost provider`() {
        composeTestRule.setContent {
            FullDownloadContent()
        }

        composeTestRule.onNodeWithText("Attack on Titan").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Pause").assertCountEquals(2)
        composeTestRule.onNodeWithContentDescription("Resume").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Cancel").assertCountEquals(3)
    }

    @Test
    fun `toast message appears when triggered alongside DownloadQueueScreen`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullDownloadContent(toastHostState = toastHostState)
        }

        composeTestRule.runOnIdle {
            toastHostState.show("Paused: Attack on Titan")
        }

        composeTestRule.onNodeWithText("Paused: Attack on Titan").assertIsDisplayed()
    }

    @Test
    fun `cancel toast message appears with ToastHost provider`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullDownloadContent(toastHostState = toastHostState)
        }

        composeTestRule.runOnIdle {
            toastHostState.show("Cancelled: Attack on Titan")
        }

        composeTestRule.onNodeWithText("Cancelled: Attack on Titan").assertIsDisplayed()
    }

    @Test
    fun `resume toast message appears with ToastHost provider`() {
        val toastHostState = ToastHostState()

        composeTestRule.setContent {
            FullDownloadContent(toastHostState = toastHostState)
        }

        composeTestRule.runOnIdle {
            toastHostState.show("Resumed: Spy x Family")
        }

        composeTestRule.onNodeWithText("Resumed: Spy x Family").assertIsDisplayed()
    }
}
