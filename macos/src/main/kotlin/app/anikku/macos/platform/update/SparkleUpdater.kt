package app.anikku.macos.platform.update

import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.notification.NotificationType
import com.sun.jna.Library
import com.sun.jna.Native
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * JNA interface to the Swift Sparkle helper dylib.
 *
 * Compiled from src/main/swift/SparkleHelper.swift via
 * scripts/build-sparkle-helper.sh.
 */
interface SparkleHelperLib : Library {
    /** Initialize Sparkle with an optional feed URL (null = read from Info.plist). */
    fun sparkle_init(feedURL: String?): Boolean

    /** Show the standard Sparkle update dialog. */
    fun sparkle_checkForUpdates()

    /** Silent background check with notification if update found. */
    fun sparkle_checkInBackground()

    /** Return the configured feed URL, or null if not initialized. */
    fun sparkle_feedURL(): String?
}

/**
 * Sparkle 2 auto-updater integration for macOS.
 *
 * ## Architecture
 *
 * [Sparkle](https://sparkle-project.org/) is the standard macOS auto-update
 * framework. At app startup, we load the Swift helper dylib via JNA which
 * initializes Sparkle's SPUStandardUpdaterController.
 *
 * When Sparkle is available:
 * - Updates are checked automatically on a schedule (daily by default)
 * - "Check for Updates" shows the native Sparkle dialog
 * - Updates download in the background and install on app relaunch
 *
 * When Sparkle is NOT available (dev builds, missing framework):
 * - Falls back to [AppUpdateChecker] which queries the GitHub Releases API
 * - "Check for Updates" opens the browser to the download page
 *
 * ## Bundling
 *
 * Sparkle.framework and libSparkleHelper.dylib are bundled in the .app:
 * ```
 * Anikku.app/Contents/Frameworks/
 *   Sparkle.framework/
 *   libSparkleHelper.dylib
 * ```
 *
 * The Gradle build runs scripts/build-sparkle-helper.sh which:
 * 1. Downloads Sparkle 2.x from GitHub Releases
 * 2. Compiles the Swift helper against it
 * 3. Copies both to src/main/resources/dist/Frameworks/
 *
 * @param appUpdateChecker Fallback update checker when Sparkle is unavailable.
 * @param notificationManager Optional notification manager for showing update alerts.
 */
class SparkleUpdater(
    private val appUpdateChecker: AppUpdateChecker? = null,
    private val notificationManager: MacOSNotificationManager? = null,
) {

    /** The JNA-loaded Sparkle helper dylib, or null if unavailable. */
    private val sparkleLib: SparkleHelperLib? = loadSparkleHelper()

    /** Whether the Sparkle helper library is available and loaded. */
    val isAvailable: Boolean get() = sparkleLib != null

    // ---- Initialization ----

    /**
     * Initialize Sparkle at app startup.
     * Must be called once before any other Sparkle methods.
     *
     * @param feedURL The appcast feed URL. If null, Sparkle reads SUFeedURL
     *                from the app's Info.plist (injected by patchInfoPlist).
     */
    fun initialize(feedURL: String? = null) {
        val lib = sparkleLib
        if (lib != null) {
            logger.info { "Initializing Sparkle with feed URL: ${feedURL ?: "(from Info.plist)"}" }
            try {
                val ok = lib.sparkle_init(feedURL)
                if (ok) {
                    logger.info { "Sparkle initialized successfully" }
                } else {
                    logger.warn { "Sparkle initialization returned false" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Sparkle initialization failed" }
            }
        } else {
            logger.info { "Sparkle not available — using AppUpdateChecker fallback" }
        }
    }

    // ---- Update Checks ----

    /**
     * Check for updates silently in the background.
     *
     * If Sparkle is available, triggers a background check that shows a
     * notification when an update is found. Otherwise, delegates to
     * [AppUpdateChecker.checkForUpdate] and shows a macOS notification
     * when an update is available.
     */
    fun checkForUpdatesSilently() {
        val lib = sparkleLib
        if (lib != null) {
            logger.info { "Sparkle: starting background update check" }
            try {
                lib.sparkle_checkInBackground()
            } catch (e: Exception) {
                logger.error(e) { "Sparkle background check failed" }
                fallbackCheck()
            }
        } else {
            fallbackCheck()
        }
    }

    /**
     * Check for updates and display the update dialog.
     *
     * If Sparkle is available, opens the Sparkle update dialog.
     * Otherwise, delegates to [AppUpdateChecker.checkAndPrompt] which opens
     * the browser to the download page.
     *
     * @return true if the check was initiated.
     */
    fun checkForUpdatesWithUI(): Boolean {
        val lib = sparkleLib
        if (lib != null) {
            logger.info { "Sparkle: showing update dialog" }
            try {
                lib.sparkle_checkForUpdates()
            } catch (e: Exception) {
                logger.error(e) { "Sparkle UI check failed" }
                appUpdateChecker?.checkAndPrompt()
                return true
            }
            return true
        }

        logger.info { "Sparkle not available — using AppUpdateChecker fallback" }
        if (appUpdateChecker != null) {
            appUpdateChecker.checkAndPrompt()
            return true
        } else {
            logger.warn { "No update checker available — cannot check for updates" }
            return false
        }
    }

    /**
     * Get the configured Sparkle feed URL.
     */
    fun getFeedURL(): String? {
        val lib = sparkleLib
        if (lib != null) {
            return try {
                lib.sparkle_feedURL()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to read Sparkle feed URL" }
                null
            }
        }
        return "https://anikku.app/sparkle/appcast.xml"
    }

    // ---- Private helpers ----

    /**
     * Fallback update check using the GitHub API.
     */
    private fun fallbackCheck() {
        logger.info { "Using AppUpdateChecker fallback for update check" }
        appUpdateChecker?.checkForUpdate { update ->
            if (update != null) {
                logger.info { "Update available: ${update.tagName} (${update.versionName})" }
                notificationManager?.showNotification(
                    title = "Update Available",
                    message = "Anikku ${update.versionName} is ready to download",
                    type = NotificationType.INFO,
                    onClick = {
                        appUpdateChecker?.openDownloadPage(update)
                    },
                )
            }
        }
    }

    companion object {
        /**
         * Attempt to load the Sparkle helper dylib via JNA.
         *
         * Search order:
         * 1. Anikku.app/Contents/Frameworks/libSparkleHelper.dylib (packaged)
         * 2. build/sparkle/libSparkleHelper.dylib (development)
         * 3. System library path
         */
        private fun loadSparkleHelper(): SparkleHelperLib? {
            val libName = "SparkleHelper"

            // In the packaged .app, the dylib is in Contents/Frameworks/
            // which is on the java.library.path via jpackage --java-options
            return try {
                Native.load(libName, SparkleHelperLib::class.java).also {
                    logger.info { "Sparkle helper dylib loaded via JNA" }
                }
            } catch (e: UnsatisfiedLinkError) {
                // Try explicit path for development (build/sparkle/)
                try {
                    val devPath = findDevDylibPath()
                    if (devPath != null) {
                        Native.load(devPath, SparkleHelperLib::class.java).also {
                            logger.info { "Sparkle helper dylib loaded from: $devPath" }
                        }
                    } else {
                        logger.info { "Sparkle helper dylib not available — updates via GitHub API" }
                        null
                    }
                } catch (e2: UnsatisfiedLinkError) {
                    logger.info { "Sparkle helper dylib not available — updates via GitHub API" }
                    null
                }
            }
        }

        /**
         * Find the development build path for the Sparkle helper dylib.
         */
        private fun findDevDylibPath(): String? {
            val candidates = listOf(
                "build/sparkle/libSparkleHelper.dylib",
                "macos/build/sparkle/libSparkleHelper.dylib",
                "../build/sparkle/libSparkleHelper.dylib",
                "src/main/resources/dist/Frameworks/libSparkleHelper.dylib",
                "macos/src/main/resources/dist/Frameworks/libSparkleHelper.dylib",
            )
            val cwd = System.getProperty("user.dir", ".")
            for (candidate in candidates) {
                val file = File(cwd, candidate)
                if (file.isFile) return file.absolutePath
            }
            // Also check without cwd prefix
            for (candidate in candidates) {
                val file = File(candidate)
                if (file.isFile) return file.absolutePath
            }
            return null
        }
    }
}
