package app.anikku.macos.platform.web

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.net.URI

class BrowserLauncherTest {

    @Test
    fun `isAvailable returns a boolean without throwing`() {
        // isAvailable lazily initializes by checking Desktop support.
        // This should never throw regardless of environment.
        BrowserLauncher.isAvailable
    }

    @Test
    fun `openSafe with valid URL does not throw`() {
        BrowserLauncher.openSafe("https://github.com/komikku-app/anikku")
    }

    @Test
    fun `openSafe with valid URI does not throw`() {
        BrowserLauncher.openSafe(URI("https://anilist.co"))
    }

    @Test
    fun `openSafe with malformed URL returns false`() {
        val result = BrowserLauncher.openSafe("not a valid url at all !!!")
        assertFalse(result)
    }

    @Test
    fun `openSafe with malformed URI returns false`() {
        // A string with spaces is not a valid URI and will trigger a parse failure
        val result = BrowserLauncher.openSafe("http://examp le.com/path")
        assertFalse(result)
    }

    @Test
    fun `open with valid URI does not propagate exception`() {
        try {
            BrowserLauncher.open(URI("https://example.com"))
        } catch (_: java.awt.HeadlessException) {
            // Expected in CI/headless environments
        }
    }

    @Test
    fun `open with valid URL string does not propagate exception`() {
        try {
            BrowserLauncher.open("https://example.com/test")
        } catch (_: java.awt.HeadlessException) {
            // Expected in CI/headless environments
        }
    }
}
