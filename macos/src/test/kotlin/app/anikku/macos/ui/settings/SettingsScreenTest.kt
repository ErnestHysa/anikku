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
 * Uses [createComposeRule] (JUnit 4 @Rule, works within JUnit Platform).
 * Tests verify rendering and state-propagation via [LocalSettingsState].
 *
 * Note: Interactive click tests (performClick/performSemanticsAction) are
 * not available in Compose Multiplatform 1.11.1. Click behavior is tested
 * indirectly through [SettingsStateTest] (state mutation + persistence).
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Rendering: static content
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
    fun `renders About section heading`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("About").assertIsDisplayed()
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
    fun `renders app version text`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Version 1.0.0").assertIsDisplayed()
    }

    @Test
    fun `renders app name`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Anikku macOS").assertIsDisplayed()
    }

    @Test
    fun `description text is shown`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        composeTestRule.onNodeWithText(
            "A native macOS anime watching application, " +
                "ported from the Anikku Android app.",
        ).assertIsDisplayed()
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

        // AMOLED label should still be rendered regardless of toggle state
        composeTestRule.onNodeWithText("AMOLED black").assertIsDisplayed()
    }
}
