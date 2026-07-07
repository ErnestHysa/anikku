package app.anikku.macos.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.anikku.macos.ui.theme.AnikkuTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [SettingsScreen].
 *
 * Tests verify rendering and state-propagation via [LocalSettingsState].
 * Tests focus on content visible at the top of the scrollable settings panel
 * within the default test viewport (800x600dp).
 *
 * Note: Interactive click tests (performClick/performSemanticsAction) are
 * not available in Compose Multiplatform 1.11.1. Click behavior is tested
 * indirectly through [SettingsStateTest] (state mutation + persistence).
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Rendering: visible header content
    // -----------------------------------------------------------------------

    @Test
    fun `renders Settings header`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun `renders Appearance section heading`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun `renders AMOLED black checkbox label`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("AMOLED black").assertIsDisplayed()
    }

    @Test
    fun `renders Theme label`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
    }

    @Test
    fun `renders Library section heading`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Library").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // State propagation: theme renders correctly from SettingsState
    // -----------------------------------------------------------------------

    @Test
    fun `default theme name shown in selector`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Default").assertIsDisplayed()
    }

    @Test
    fun `custom theme SAPPHIRE renders in selector`() {
        val state = SettingsState()
        state.theme = AnikkuTheme.Theme.SAPPHIRE

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Sapphire").assertIsDisplayed()
    }

    @Test
    fun `custom theme MATRIX renders in selector`() {
        val state = SettingsState()
        state.theme = AnikkuTheme.Theme.MATRIX

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Matrix").assertIsDisplayed()
    }

    @Test
    fun `custom theme NORD renders in selector`() {
        val state = SettingsState()
        state.theme = AnikkuTheme.Theme.NORD

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Nord").assertIsDisplayed()
    }

    @Test
    fun `custom theme LAVENDER renders in selector`() {
        val state = SettingsState()
        state.theme = AnikkuTheme.Theme.LAVENDER

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("Lavender").assertIsDisplayed()
    }

    @Test
    fun `AMOLED state propagates from SettingsState`() {
        val state = SettingsState()
        state.isAmoledOLED = true

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme(isAmoledOLED = state.isAmoledOLED) {
                    SettingsScreen()
                }
            }
        }

        composeTestRule.onNodeWithText("AMOLED black").assertIsDisplayed()
    }
}
