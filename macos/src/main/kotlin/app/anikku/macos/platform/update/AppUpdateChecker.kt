package app.anikku.macos.platform.update

import app.anikku.macos.platform.web.BrowserLauncher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * GitHub-based app update checker for macOS.
 *
 * Checks the Anikku GitHub releases page for new versions.
 * On macOS, prompts the user to download the latest .dmg file.
 *
 * ## Usage
 *
 * ```kotlin
 * val updater = AppUpdateChecker(
 *     currentVersion = "1.0.0",
 *     repoOwner = "ErnestHysa",
 *     repoName = "anikku",
 * )
 *
 * // Check for updates (non-blocking)
 * updater.checkForUpdate { update ->
 *     if (update != null) {
 *         println("Update available: ${update.tagName}")
 *     }
 * }
 *
 * // Blocking check
 * val update = updater.checkForUpdateSync()
 * ```
 */
class AppUpdateChecker(
    private val currentVersion: String,
    private val repoOwner: String = "ErnestHysa",
    private val repoName: String = "anikku",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    /** For testing: override the GitHub API base URL. */
    private val githubApiBase: String = "https://api.github.com",
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Check for updates asynchronously via callback.
     *
     * @param onResult Callback with the update info, or null if no update.
     */
    fun checkForUpdate(onResult: (UpdateInfo?) -> Unit) {
        Thread {
            try {
                val update = checkForUpdateSync()
                onResult(update)
            } catch (e: Exception) {
                logger.error(e) { "Failed to check for updates" }
                onResult(null)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Check for updates synchronously.
     *
     * @return Update info if a newer version is available, null otherwise.
     */
    fun checkForUpdateSync(): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url("$githubApiBase/repos/$repoOwner/$repoName/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Anikku-macOS/$currentVersion")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                logger.warn { "GitHub API returned ${response.code}: $bodyString" }
                return null
            }

            val release = json.parseToJsonElement(bodyString).jsonObject

            val tagName = release["tag_name"]?.jsonPrimitive?.content ?: return null
            val versionNumber = tagName.removePrefix("v").removePrefix("r")

            // Compare versions (simple string comparison works for semver)
            if (versionNumber <= currentVersion) {
                logger.info { "App is up to date ($currentVersion)" }
                return null
            }

            val htmlUrl = release["html_url"]?.jsonPrimitive?.content ?: ""
            val body = release["body"]?.jsonPrimitive?.content ?: ""
            val publishedAt = release["published_at"]?.jsonPrimitive?.content ?: ""

            // Find macOS download URL
            val assets = release["assets"]?.jsonArray
            val macAsset = assets?.firstOrNull { element ->
                val name = element.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                name.contains(".dmg") || name.contains("macOS") || name.contains("mac")
            }

            val downloadUrl = macAsset?.let { element ->
                element.jsonObject["browser_download_url"]?.jsonPrimitive?.content
            } ?: htmlUrl // Fallback to release page if no DMG asset

            UpdateInfo(
                tagName = tagName,
                versionName = versionNumber,
                htmlUrl = htmlUrl,
                downloadUrl = downloadUrl,
                releaseBody = body,
                publishedAt = publishedAt,
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to check for updates" }
            null
        }
    }

    /**
     * Open the download URL in the system browser via BrowserLauncher.
     */
    fun openDownloadPage(updateInfo: UpdateInfo) {
        BrowserLauncher.openSafe(updateInfo.downloadUrl)
    }

    /**
     * Open the release page in the system browser via BrowserLauncher.
     */
    fun openReleasePage(updateInfo: UpdateInfo) {
        BrowserLauncher.openSafe(updateInfo.htmlUrl)
    }

    /**
     * Check if there's an update and open the download page if so.
     * Returns immediately via callback with whether an update was found.
     */
    fun checkAndPrompt(onResult: ((Boolean) -> Unit)? = null) {
        checkForUpdate { update ->
            if (update != null) {
                openDownloadPage(update)
                onResult?.invoke(true)
            } else {
                onResult?.invoke(false)
            }
        }
    }
}

/**
 * Information about an available update.
 */
@Serializable
data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val htmlUrl: String,
    val downloadUrl: String,
    val releaseBody: String = "",
    val publishedAt: String = "",
)
