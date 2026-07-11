package app.anikku.macos.platform.update

import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.notification.NotificationType
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Sparkle auto-updater integration for macOS.
 *
 * ## Architecture
 *
 * [Sparkle](https://sparkle-project.org/) is the standard macOS auto-update framework.
 * It checks for updates via an appcast.xml feed, downloads new versions in the background,
 * and applies them automatically.
 *
 * This class provides the update-checking interface. When Sparkle is bundled and available
 * (via a Swift helper dylib), it uses Sparkle natively. Otherwise, it falls back to
 * [AppUpdateChecker] which queries the GitHub Releases API.
 *
 * ## Current behavior (v1)
 *
 * Sparkle.framework is not yet bundled in the app. The methods in this class delegate
 * to [AppUpdateChecker] as a fallback, so "Check for Updates" works via the GitHub API
 * regardless of whether Sparkle is available.
 *
 * When [notificationManager] is provided, silent background checks will show a macOS
 * Notification Center alert when a new version is found, informing the user without
 * needing to open the About dialog.
 *
 * ## Production Sparkle integration (future)
 *
 * To enable native Sparkle updates:
 * 1. Build a Swift helper dylib that wraps SPUStandardUpdaterController
 * 2. Bundle it with Sparkle.framework in the app's Frameworks/ directory
 * 3. Load it via JNA: `Native.load("SparkleHelper", SparkleHelper::class.java)`
 *
 * See the documentation in this file for the complete setup instructions.
 *
 * @param appUpdateChecker Fallback update checker when Sparkle is unavailable.
 * @param notificationManager Optional notification manager for showing update alerts.
 */
class SparkleUpdater(
    private val appUpdateChecker: AppUpdateChecker? = null,
    private val notificationManager: MacOSNotificationManager? = null,
) {

    /**
     * Whether the Sparkle helper library is available and loaded.
     *
     * Currently always false — this requires a Swift helper dylib to be built
     * and bundled with the app. See class documentation for setup instructions.
     */
    val isAvailable: Boolean get() = isSparkleAvailable

    /**
     * Check for updates silently in the background.
     *
     * If Sparkle is available, triggers a background check that shows a notification
     * when an update is found. Otherwise, delegates to [AppUpdateChecker.checkForUpdate]
     * and shows a macOS notification when an update is available.
     */
    fun checkForUpdatesSilently() {
        if (isSparkleAvailable) {
            triggerSparkleBackgroundCheck()
        } else {
            logger.info { "Sparkle not available — using AppUpdateChecker fallback" }
            appUpdateChecker?.checkForUpdate { update ->
                if (update != null) {
                    logger.info { "Update available: ${update.tagName} (${update.versionName})" }
                    // Show macOS notification so the user is informed without opening the About dialog
                    notificationManager?.showNotification(
                        title = "Update Available",
                        message = "Anikku ${update.versionName} is ready to download",
                        type = NotificationType.INFO,
                        onClick = {
                            // Open the download page when the notification is clicked
                            appUpdateChecker?.openDownloadPage(update)
                        },
                    )
                }
            }
        }
    }

    /**
     * Check for updates and display the standard Sparkle update window.
     *
     * If Sparkle is available, opens the Sparkle update dialog.
     * Otherwise, delegates to [AppUpdateChecker.checkAndPrompt] which opens the
     * browser to the download page.
     *
     * @return true if the check was initiated (via either Sparkle or fallback).
     */
    fun checkForUpdatesWithUI(): Boolean {
        if (isSparkleAvailable) {
            triggerSparkleUICheck()
            return true
        }

        logger.info { "Sparkle not available — using AppUpdateChecker fallback" }
        return if (appUpdateChecker != null) {
            appUpdateChecker.checkAndPrompt()
            true
        } else {
            logger.warn { "No update checker available — cannot check for updates" }
            false
        }
    }

    /**
     * Get the configured Sparkle feed URL from Info.plist.
     *
     * When Sparkle is available, reads SUFeedURL from the app's Info.plist.
     * Otherwise, constructs the URL from the appcast host.
     */
    fun getFeedURL(): String? {
        if (isSparkleAvailable) {
            return readSparkleFeedURL()
        }
        // Return the expected URL from Info.plist (matches the patchInfoPlist task)
        return "https://anikku.app/sparkle/appcast.xml"
    }

    // ---- Sparkle native integration (future) ----

    /**
     * Internal flag that would be set to true when Sparkle.framework and
     * the Swift helper dylib are successfully loaded via JNA.
     *
     * For now, this is always false. To enable:
     *
     * ```kotlin
     * interface SparkleHelper : Library {
     *     fun sparkle_checkForUpdates()
     *     fun sparkle_checkForUpdatesInBackground()
     *     fun sparkle_feedURL(): String?
     * }
     *
     * private val sparkleHelper = try {
     *     Native.load("SparkleHelper", SparkleHelper::class.java)
     * } catch (e: UnsatisfiedLinkError) {
     *     logger.warn { "SparkleHelper dylib not found — using GitHub fallback" }
     *     null
     * }
     * ```
     */
    private val isSparkleAvailable: Boolean = false

    /**
     * Call the Swift helper's sparkle_checkForUpdatesInBackground().
     * Only called when [isSparkleAvailable] is true.
     */
    private fun triggerSparkleBackgroundCheck() {
        // TODO: sparkleHelper?.sparkle_checkForUpdatesInBackground()
        logger.info { "Sparkle native check would run here (requires Swift helper dylib)" }
    }

    /**
     * Call the Swift helper's sparkle_checkForUpdates() to show the update dialog.
     * Only called when [isSparkleAvailable] is true.
     */
    private fun triggerSparkleUICheck() {
        // TODO: sparkleHelper?.sparkle_checkForUpdates()
        logger.info { "Sparkle native UI would open here (requires Swift helper dylib)" }
    }

    /**
     * Read SUFeedURL from the app's Info.plist via ObjC.
     * Only called when [isSparkleAvailable] is true.
     */
    private fun readSparkleFeedURL(): String? {
        // TODO: Read from NSBundle.mainBundle.objectForInfoDictionaryKey("SUFeedURL")
        return null
    }
}
