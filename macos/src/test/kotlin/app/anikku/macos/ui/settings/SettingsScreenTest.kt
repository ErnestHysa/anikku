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
 * Tests verify the settings UI renders with expected content and responds
 * to state changes via [LocalSettingsState].
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun `default theme name shown in selector`() {
        composeTestRule.setContent {
            AnikkuTheme {
                SettingsScreen()
            }
        }

        // Default theme display name is "Default"
        composeTestRule.onNodeWithText("Default").assertIsDisplayed()
    }

    @Test
    fun `custom theme via LocalSettingsState renders in selector`() {
        val state = SettingsState()
        state.theme = AnikkuTheme.Theme.SAPPHIRE

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsState provides state) {
                AnikkuTheme {
                    SettingsScreen()
                }
            }
        }

        // The SelectItem OutlinedTextField shows the current theme's displayName
        composeTestRule.onNodeWithText("Sapphire").assertIsDisplayed()
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
}
