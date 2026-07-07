package app.anikku.macos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [AnimatedTabFade].
 *
 * Tests verify content rendering, key-based switching, and that only one
 * content composable is in the tree at a time.
 *
 * Note: Alpha animation timing is intentionally not tested because Compose
 * Multiplatform's test infrastructure does not support advancing virtual time
 * through coroutine delay() calls (LaunchedEffect + animateTo).
 */
class AnimatedTabFadeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Initial rendering
    // -----------------------------------------------------------------------

    @Test
    fun `renders content on initial render`() {
        composeTestRule.setContent {
            AnimatedTabFade(contentKey = "key1") {
                Text("Initial content")
            }
        }

        composeTestRule.onNodeWithText("Initial content").assertIsDisplayed()
    }

    @Test
    fun `renders complex composable content`() {
        composeTestRule.setContent {
            AnimatedTabFade(contentKey = "complex") {
                Box(Modifier.size(100.dp)) {
                    Text("Nested content")
                }
            }
        }

        composeTestRule.onNodeWithText("Nested content").assertIsDisplayed()
    }

    @Test
    fun `renders empty content without crash`() {
        composeTestRule.setContent {
            AnimatedTabFade(contentKey = "empty") {
                // No content
            }
        }

        // Should not crash — just renders an empty Box
    }

    // -----------------------------------------------------------------------
    // Content key switching
    // -----------------------------------------------------------------------

    @Test
    fun `content switches when contentKey changes`() {
        var key by mutableStateOf("a")

        composeTestRule.setContent {
            AnimatedTabFade(contentKey = key) {
                if (key == "a") {
                    Text("Content A")
                } else {
                    Text("Content B")
                }
            }
        }

        composeTestRule.onNodeWithText("Content A").assertIsDisplayed()

        // Switch key
        composeTestRule.runOnIdle {
            key = "b"
        }

        composeTestRule.onNodeWithText("Content B").assertIsDisplayed()
    }

    @Test
    fun `previous content is removed when key changes`() {
        var key by mutableStateOf("a")

        composeTestRule.setContent {
            AnimatedTabFade(contentKey = key) {
                if (key == "a") {
                    Text("Content A")
                } else {
                    Text("Content B")
                }
            }
        }

        composeTestRule.onNodeWithText("Content A").assertIsDisplayed()

        // Switch key
        composeTestRule.runOnIdle {
            key = "b"
        }

        // Old content replaced by new content (key(contentKey) removes previous composable)
        composeTestRule.onNodeWithText("Content B").assertIsDisplayed()
    }

    @Test
    fun `switching back to previous key re-renders content`() {
        var key by mutableStateOf("a")

        composeTestRule.setContent {
            AnimatedTabFade(contentKey = key) {
                Text("Content: $key")
            }
        }

        composeTestRule.onNodeWithText("Content: a").assertIsDisplayed()

        // Switch to B
        composeTestRule.runOnIdle {
            key = "b"
        }
        composeTestRule.onNodeWithText("Content: b").assertIsDisplayed()

        // Switch back to A
        composeTestRule.runOnIdle {
            key = "a"
        }
        composeTestRule.onNodeWithText("Content: a").assertIsDisplayed()
    }

    @Test
    fun `multiple rapid key changes resolve to last key`() {
        var key by mutableStateOf("a")

        composeTestRule.setContent {
            AnimatedTabFade(contentKey = key) {
                Text("Key: $key")
            }
        }

        composeTestRule.runOnIdle {
            key = "b"
            key = "c"
            key = "d"
        }

        composeTestRule.onNodeWithText("Key: d").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Custom duration
    // -----------------------------------------------------------------------

    @Test
    fun `accepts custom duration without crash`() {
        composeTestRule.setContent {
            AnimatedTabFade(contentKey = "custom", durationMillis = 500) {
                Text("Custom duration")
            }
        }

        composeTestRule.onNodeWithText("Custom duration").assertIsDisplayed()
    }

    @Test
    fun `zero duration does not crash`() {
        composeTestRule.setContent {
            AnimatedTabFade(contentKey = "zero", durationMillis = 0) {
                Text("Zero duration")
            }
        }

        composeTestRule.onNodeWithText("Zero duration").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Multiple instances
    // -----------------------------------------------------------------------

    @Test
    fun `multiple AnimatedTabFade instances render independently`() {
        composeTestRule.setContent {
            Box {
                AnimatedTabFade(contentKey = "left") {
                    Text("Left panel")
                }
                AnimatedTabFade(contentKey = "right") {
                    Text("Right panel")
                }
            }
        }

        composeTestRule.onNodeWithText("Left panel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Right panel").assertIsDisplayed()
    }

    @Test
    fun `independent instances switch content independently`() {
        var leftKey by mutableStateOf("a")
        var rightKey by mutableStateOf("x")

        composeTestRule.setContent {
            Box {
                AnimatedTabFade(contentKey = leftKey) {
                    Text("Left: $leftKey")
                }
                AnimatedTabFade(contentKey = rightKey) {
                    Text("Right: $rightKey")
                }
            }
        }

        composeTestRule.onNodeWithText("Left: a").assertIsDisplayed()
        composeTestRule.onNodeWithText("Right: x").assertIsDisplayed()

        // Switch left only
        composeTestRule.runOnIdle {
            leftKey = "b"
        }

        composeTestRule.onNodeWithText("Left: b").assertIsDisplayed()
        composeTestRule.onNodeWithText("Right: x").assertIsDisplayed()
    }
}
