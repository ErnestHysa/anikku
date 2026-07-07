package app.anikku.macos.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * macOS share utilities for the Anikku desktop app.
 *
 * Provides clipboard copying and macOS Notification Center notifications,
 * used as the primary "Share" mechanism on macOS Desktop.
 */
object MacOSShareUtil {

    /**
     * Copies [text] to the system clipboard.
     * @return true if the copy succeeded
     */
    fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Shows a macOS Notification Center notification using `osascript`.
     *
     * This fires a script to `display notification` which appears in
     * Notification Center, actionable from the menu bar bell icon.
     *
     * @param title   Notification title (bold)
     * @param message Notification body text
     * @return true if the notification was posted successfully
     */
    fun showMacOSNotification(title: String, message: String): Boolean {
        return try {
            val escapedTitle = title.replace("\"", "\\\"")
            val escapedMessage = message.replace("\"", "\\\"")
            val script = """
                display notification "$escapedMessage" with title "$escapedTitle"
            """.trimIndent()

            val process = ProcessBuilder("osascript", "-e", script)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Shares [text] by copying it to the clipboard and posting a
     * macOS Notification Center alert.
     *
     * This is the primary share action on Desktop where no native
     * Share sheet is available through pure JVM APIs.
     *
     * @param title       Notification title (e.g. "Anikku")
     * @param text        The URL or text to share
     * @param description Short description for the notification body
     * @return true if clipboard copy succeeded; notification is best-effort
     */
    fun shareUrl(
        title: String,
        text: String,
        description: String = "URL copied to clipboard",
    ): Boolean {
        val copied = copyToClipboard(text)
        if (copied) {
            showMacOSNotification(title, description)
        }
        return copied
    }
}
