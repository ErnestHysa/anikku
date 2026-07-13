package app.anikku.macos.platform.update

import app.anikku.macos.platform.web.BrowserLauncher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [AppUpdateChecker].
 *
 * Uses a mock OkHttp interceptor to simulate GitHub API responses
 * without making actual network calls.
 */
class AppUpdateCheckerTest {

    private var lastInterceptor: LastCallInterceptor? = null
    private lateinit var checker: AppUpdateChecker

    /**
     * OkHttp interceptor that captures the last request and returns a custom response.
     */
    class LastCallInterceptor : Interceptor {
        var statusCode: Int = 200
        var responseBody: String = "{}"

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(if (statusCode == 200) "OK" else "Error")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    @BeforeEach
    fun setUp() {
        BrowserLauncher.testMode = false
        lastInterceptor = LastCallInterceptor()
        val client = OkHttpClient.Builder()
            .addInterceptor(lastInterceptor!!)
            .build()
        checker = AppUpdateChecker(
            currentVersion = "1.0.0",
            client = client,
            githubApiBase = "http://mock/api",
        )
    }

    @AfterEach
    fun tearDown() {
        BrowserLauncher.testMode = false
        BrowserLauncher.lastOpenedUri = null
    }

    @Test
    fun `returns update when newer version available`() {
        lastInterceptor!!.statusCode = 200
        lastInterceptor!!.responseBody = """
        {
            "tag_name": "v2.0.0",
            "html_url": "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            "body": "New features and bug fixes",
            "published_at": "2026-07-01T00:00:00Z",
            "assets": [
                {
                    "name": "Anikku-2.0.0.dmg",
                    "browser_download_url": "https://github.com/ErnestHysa/anikku/releases/download/v2.0.0/Anikku-2.0.0.dmg"
                }
            ]
        }
        """.trimIndent()

        val update = checker.checkForUpdateSync()

        assertNotNull(update, "Should find an update when a newer version exists")
        assertEquals("v2.0.0", update?.tagName)
        assertEquals("2.0.0", update?.versionName)
        assertEquals(
            "https://github.com/ErnestHysa/anikku/releases/download/v2.0.0/Anikku-2.0.0.dmg",
            update?.downloadUrl,
        )
    }

    @Test
    fun `returns null when already on latest version`() {
        lastInterceptor!!.statusCode = 200
        lastInterceptor!!.responseBody = """
        {
            "tag_name": "v1.0.0",
            "html_url": "https://github.com/ErnestHysa/anikku/releases/tag/v1.0.0",
            "body": "Initial release",
            "published_at": "2026-06-01T00:00:00Z",
            "assets": []
        }
        """.trimIndent()

        val update = checker.checkForUpdateSync()

        assertNull(update, "Should return null when current version matches latest")
    }

    @Test
    fun `returns null on API error`() {
        lastInterceptor!!.statusCode = 403
        lastInterceptor!!.responseBody = """{"message": "Rate limit exceeded"}"""

        val update = checker.checkForUpdateSync()

        assertNull(update, "Should return null on API error")
    }

    @Test
    fun `falls back to release page when no DMG asset found`() {
        lastInterceptor!!.statusCode = 200
        lastInterceptor!!.responseBody = """
        {
            "tag_name": "v2.0.0",
            "html_url": "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            "body": "New release without DMG",
            "published_at": "2026-07-01T00:00:00Z",
            "assets": []
        }
        """.trimIndent()

        val update = checker.checkForUpdateSync()

        assertNotNull(update, "Should still return update info even without DMG asset")
        assertEquals(
            "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            update?.downloadUrl,
            "Should fall back to release page URL when no DMG asset exists",
        )
    }

    @Test
    fun `openDownloadPage delegates to BrowserLauncher`() {
        BrowserLauncher.testMode = true
        val update = UpdateInfo(
            tagName = "v2.0.0",
            versionName = "2.0.0",
            htmlUrl = "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            downloadUrl = "https://github.com/ErnestHysa/anikku/releases/download/v2.0.0/Anikku-2.0.0.dmg",
        )

        // Should not throw — BrowserLauncher.openSafe handles headless environments gracefully
        checker.openDownloadPage(update)
    }

    @Test
    fun `openReleasePage delegates to BrowserLauncher`() {
        BrowserLauncher.testMode = true
        val update = UpdateInfo(
            tagName = "v2.0.0",
            versionName = "2.0.0",
            htmlUrl = "https://github.com/ErnestHysa/anikku/releases/tag/v2.0.0",
            downloadUrl = "https://github.com/ErnestHysa/anikku/releases/download/v2.0.0/Anikku-2.0.0.dmg",
        )

        checker.openReleasePage(update)
    }
}
