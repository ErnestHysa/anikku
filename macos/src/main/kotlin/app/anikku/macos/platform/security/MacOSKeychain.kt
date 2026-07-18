package app.anikku.macos.platform.security

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream

private val logger = KotlinLogging.logger {}

/**
 * macOS Keychain wrapper — stores sensitive strings (OAuth tokens, client secrets)
 * in the user's login Keychain via the `security` command-line tool.
 *
 * This is the macOS equivalent of Android's EncryptedSharedPreferences / Keystore.
 * Data is stored in the user's Keychain, which is encrypted at rest and only
 * accessible while the user is logged in to macOS.
 *
 * ## Usage
 *
 * ```kotlin
 * val keychain = MacOSKeychain(service = "anikku-tracker")
 * keychain.store("myanimelist-token", "access_token_value")
 * val token = keychain.retrieve("myanimelist-token")
 * keychain.delete("myanimelist-token")
 * ```
 *
 * ## Security properties
 *
 * - Data is encrypted with the user's login password (FileVault-class encryption)
 * - Accessible only while the user is logged in
 * - Survives app reinstallation (stored in system Keychain, not app sandbox)
 * - Not accessible by other apps without explicit Keychain access
 *
 * ## Thread safety
 *
 * All methods are safe to call from any thread. Each call spawns a short-lived
 * `security` process. Avoid calling from the main thread in tight loops.
 */
class MacOSKeychain(
    /** Keychain service name — scopes entries so they don't collide with other apps. */
    private val service: String = "anikku",
    /** Keychain account name — groups related entries under one account. */
    private val account: String = "anikku-app",
) {

    /** Whether the `security` CLI tool is available on this system. */
    val isAvailable: Boolean by lazy {
        try {
            val process = ProcessBuilder("which", "security")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check security CLI availability" }
            false
        }
    }

    /**
     * Store a value in the Keychain under the given key.
     * If an entry with the same key already exists, it is updated.
     *
     * @param key The key to store the value under (e.g., "tracker_token_myanimelist").
     * @param value The value to store.
     * @return true if the value was stored successfully.
     */
    fun store(key: String, value: String): Boolean {
        if (value.isBlank()) return delete(key)

        return try {
            // Try to update existing item first
            val updateResult = runCommand(
                "security", "add-generic-password",
                "-a", account,
                "-s", "$service-$key",
                "-w", value,
                "-U", // Update if exists
                "-j", service, // Service label for organization
            )

            if (updateResult.exitCode == 0) {
                logger.debug { "Keychain: stored $key (${value.length} chars)" }
                true
            } else {
                logger.warn { "Keychain: failed to store $key (exit ${updateResult.exitCode}): ${updateResult.stderr.take(100)}" }
                false
            }
        } catch (e: Exception) {
            logger.warn(e) { "Keychain: error storing $key" }
            false
        }
    }

    /**
     * Retrieve a value from the Keychain by key.
     *
     * @param key The key to look up.
     * @return The stored value, or null if no entry exists.
     */
    fun retrieve(key: String): String? {
        return try {
            val result = runCommand(
                "security", "find-generic-password",
                "-a", account,
                "-s", "$service-$key",
                "-w", // Output only the password
            )

            if (result.exitCode == 0) {
                val value = result.stdout.trimEnd('\n')
                if (value.isNotBlank()) {
                    logger.debug { "Keychain: retrieved $key (${value.length} chars)" }
                    value
                } else {
                    null
                }
            } else {
                logger.debug { "Keychain: no entry found for $key (exit ${result.exitCode})" }
                null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Keychain: error retrieving $key" }
            null
        }
    }

    /**
     * Delete a value from the Keychain by key.
     *
     * @param key The key to delete.
     * @return true if the entry was deleted or didn't exist.
     */
    fun delete(key: String): Boolean {
        return try {
            val result = runCommand(
                "security", "delete-generic-password",
                "-a", account,
                "-s", "$service-$key",
            )

            val success = result.exitCode == 0 || result.exitCode == 44 // 44 = item not found
            if (success) {
                logger.debug { "Keychain: deleted $key" }
            } else {
                logger.warn { "Keychain: failed to delete $key (exit ${result.exitCode})" }
            }
            success
        } catch (e: Exception) {
            logger.warn(e) { "Keychain: error deleting $key" }
            false
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun runCommand(vararg args: String): CommandResult {
        val process = ProcessBuilder(*args)
            .redirectErrorStream(false)
            .start()

        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()

        process.inputStream.transferTo(stdoutStream)
        process.errorStream.transferTo(stderrStream)

        val exitCode = process.waitFor()

        return CommandResult(
            exitCode = exitCode,
            stdout = stdoutStream.toString("UTF-8"),
            stderr = stderrStream.toString("UTF-8"),
        )
    }
}
