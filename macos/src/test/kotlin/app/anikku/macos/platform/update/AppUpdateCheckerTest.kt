package app.anikku.macos.platform.update

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppUpdateCheckerTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var checker: AppUpdateChecker

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        checker = AppUpdateChecker(
            currentVersion = "1.0.0",
            repoOwner = "test-owner",
            repoName = "test-repo",
            client = okhttp3.OkHttpClient.Builder().build(),
            githubApiBase = mockServer.url("/").toString().trimEnd('/'),
        )
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `no update when version is current`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{
                    "tag_name": "v1.0.0",
                    "html_url": "https://github.com/test-owner/test-repo/releases/tag/v1.0.0",
                    "body": "Initial release",
                    "published_at": "2026-01-01T00:00:00Z"
                }"""),
        )

        val update = checker.checkForUpdateSync()
        assertNull(update)
    }

    @Test
    fun `no update when version is newer`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{
                    "tag_name": "v0.9.0",
                    "html_url": "https://github.com/test-owner/test-repo/releases/tag/v0.9.0"
                }"""),
        )

        val update = checker.checkForUpdateSync()
        assertNull(update)
    }

    @Test
    fun `returns update info when newer version available`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{
                    "tag_name": "v2.0.0",
                    "html_url": "https://github.com/test-owner/test-repo/releases/tag/v2.0.0",
                    "body": "Major update with new features",
                    "published_at": "2026-06-15T00:00:00Z",
                    "assets": [
                        {"name": "Anikku-2.0.0.dmg", "browser_download_url": "https://github.com/test-owner/test-repo/releases/download/v2.0.0/Anikku-2.0.0.dmg"}
                    ]
                }"""),
        )

        val update = checker.checkForUpdateSync()
        assertNotNull(update)
        assertEquals("v2.0.0", update?.tagName)
        assertEquals("2.0.0", update?.versionName)
        assertEquals("https://github.com/test-owner/test-repo/releases/download/v2.0.0/Anikku-2.0.0.dmg", update?.downloadUrl)
    }

    @Test
    fun `returns release page link when no dmg asset`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{
                    "tag_name": "v2.0.0",
                    "html_url": "https://github.com/test-owner/test-repo/releases/tag/v2.0.0",
                    "assets": []
                }"""),
        )

        val update = checker.checkForUpdateSync()
        assertNotNull(update)
        assertEquals("https://github.com/test-owner/test-repo/releases/tag/v2.0.0", update?.downloadUrl)
    }

    @Test
    fun `returns null on API error`() {
        mockServer.enqueue(MockResponse().setResponseCode(403).setBody("""{"message": "API rate limit exceeded"}"""))

        val update = checker.checkForUpdateSync()
        assertNull(update)
    }

    @Test
    fun `returns null on invalid response`() {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val update = checker.checkForUpdateSync()
        assertNull(update)
    }
}
