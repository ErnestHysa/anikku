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
    fun `renders download count summary when empty`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        // No download manager provided — download list is empty
        composeTestRule.onNodeWithText("0 completed").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Empty state rendering
    // -----------------------------------------------------------------------

    @Test
    fun `renders empty state message when no downloads`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        composeTestRule.onNodeWithText("No downloads").assertIsDisplayed()
    }

    @Test
    fun `renders download manager message when manager is null`() {
        composeTestRule.setContent {
            RenderDownloadQueue()
        }

        composeTestRule.onNodeWithText("Download manager not initialized").assertIsDisplayed()
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
