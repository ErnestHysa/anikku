package app.anikku.macos.platform.auth

import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSKeychain
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Persists OAuth tokens for tracker services using [MacOSPreferenceStore] for
 * non-sensitive metadata and [MacOSKeychain] for sensitive secrets.
 *
 * ## Storage Layout
 *
 * | Data                | Sensitive? | Storage               | Key                        |
 * |---------------------|------------|-----------------------|----------------------------|
 * | Access token        | ✅ Yes     | macOS Keychain        | `tracker_token_<name>`     |
 * | Refresh token       | ✅ Yes     | macOS Keychain        | (same entry)               |
 * | Client ID           | ✅ Yes     | macOS Keychain        | `creds_id_<name>`          |
 * | Client Secret       | ✅ Yes     | macOS Keychain        | `creds_secret_<name>`      |
 * | Username            | ❌ No      | preferences.json      | `tracker_user_<name>`      |
 * | Token metadata      | ❌ No      | preferences.json      | `tracker_meta_<name>`      |
 * | Login status        | ❌ No      | preferences.json      | (computed from user key)   |
 *
 * Each tracker (myanimelist, anilist, kitsu, shikimori) stores its sensitive
 * secrets in the macOS Keychain and non-sensitive metadata in preferences.json.
 *
 * @param preferenceStore For non-sensitive metadata (username, scopes, etc.)
 * @param keychain For sensitive secrets (tokens, credentials). If null or
 *                 unavailable, falls back to preferenceStore only.
 */
class TrackerTokenStore(
    private val preferenceStore: MacOSPreferenceStore,
    private val keychain: MacOSKeychain? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** Whether the Keychain is available for secure storage. */
    private val useKeychain: Boolean get() = keychain != null && keychain.isAvailable

    private val knownTrackers = listOf("myanimelist", "anilist", "kitsu", "shikimori")

    /**
     * All trackers with their login status.
     */
    data class TrackerLoginStatus(
        val tracker: String,
        val displayName: String,
        val isLoggedIn: Boolean,
        val username: String? = null,
    )

    /**
     * Get the login status for all known trackers.
     */
    fun getAllStatuses(): List<TrackerLoginStatus> = knownTrackers.map { tracker ->
        TrackerLoginStatus(
            tracker = tracker,
            displayName = displayName(tracker),
            isLoggedIn = getTokens(tracker) != null,
            username = getUsername(tracker),
        )
    }

    /**
     * Check if a tracker has stored tokens.
     */
    fun isLoggedIn(tracker: String): Boolean {
        return if (useKeychain) {
            getRawTokenFromKeychain(tracker) != null
        } else {
            getTokensFromPrefs(tracker) != null
        }
    }

    /**
     * Load stored tokens for a tracker.
     * Priority: Keychain > preferences.json.
     */
    fun getTokens(tracker: String): StoredToken? {
        return if (useKeychain) {
            getTokensFromKeychain(tracker)
        } else {
            getTokensFromPrefs(tracker)
        }
    }

    /**
     * Save tokens for a tracker.
     * Stores sensitive fields in Keychain (when available) and
     * non-sensitive metadata in preferences.json.
     */
    fun saveTokens(tracker: String, token: TokenResponse) {
        val metadata = TokenMetadata(
            tokenType = token.tokenType,
            expiresIn = token.expiresIn,
            scope = token.scope,
            createdAt = token.createdAt,
        )
        if (useKeychain) {
            // Store access + refresh tokens as JSON blob in Keychain
            val tokenBlob = json.encodeToString(StoredTokenBlob(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
            ))
            keychain!!.store("tracker_token_$tracker", tokenBlob)
            // Save metadata to preferences
            preferenceStore.getString("tracker_meta_$tracker", "").set(json.encodeToString(metadata))
            logger.info { "Tokens saved for $tracker (Keychain)" }
        } else {
            // Fallback: store everything in preferences (legacy)
            val stored = StoredToken(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                tokenType = token.tokenType,
                expiresIn = token.expiresIn,
                scope = token.scope,
                createdAt = token.createdAt,
            )
            preferenceStore.getString(key(tracker), "").set(json.encodeToString(stored))
            logger.info { "Tokens saved for $tracker (preferences fallback)" }
        }
    }

    /**
     * Save tokens with username from a successful validation.
     */
    fun saveTokensWithUsername(tracker: String, token: TokenResponse, username: String) {
        saveTokens(tracker, token)
        // Username is non-sensitive — store in preferences regardless
        preferenceStore.getString("tracker_user_$tracker", "").set(username)
        logger.info { "Username saved for $tracker: $username" }
    }

    /**
     * Get the stored username for a tracker, if available.
     */
    fun getUsername(tracker: String): String? {
        val raw = preferenceStore.getString("tracker_user_$tracker", "").get()
        return raw.ifBlank { null }
    }

    /**
     * Remove stored tokens for a tracker (logout).
     */
    fun removeTokens(tracker: String) {
        if (useKeychain) {
            keychain!!.delete("tracker_token_$tracker")
            logger.info { "Tokens removed for $tracker (Keychain)" }
        } else {
            preferenceStore.getString(key(tracker), "").delete()
            logger.info { "Tokens removed for $tracker (preferences)" }
        }
        // Always clean up metadata and username
        preferenceStore.getString("tracker_meta_$tracker", "").delete()
        preferenceStore.getString("tracker_user_$tracker", "").delete()
    }

    // -----------------------------------------------------------------------
    // OAuth client credential persistence (always Keychain when available)
    // -----------------------------------------------------------------------

    /**
     * Save OAuth client credentials (client ID and secret) for a tracker.
     */
    fun saveClientCredentials(tracker: String, clientId: String, clientSecret: String) {
        if (useKeychain) {
            keychain!!.store("creds_id_$tracker", clientId)
            keychain!!.store("creds_secret_$tracker", clientSecret)
            logger.info { "OAuth client credentials saved for $tracker (Keychain)" }
        } else {
            preferenceStore.getString("creds_id_$tracker", "").set(clientId)
            preferenceStore.getString("creds_secret_$tracker", "").set(clientSecret)
            logger.info { "OAuth client credentials saved for $tracker (preferences fallback)" }
        }
    }

    /**
     * Load stored OAuth client credentials for a tracker.
     * Returns null if no credentials have been saved.
     */
    fun getClientCredentials(tracker: String): Pair<String, String>? {
        return if (useKeychain) {
            val clientId = keychain!!.retrieve("creds_id_$tracker") ?: return null
            val clientSecret = keychain!!.retrieve("creds_secret_$tracker") ?: return null
            Pair(clientId, clientSecret)
        } else {
            val clientId = preferenceStore.getString("creds_id_$tracker", "").get()
            val clientSecret = preferenceStore.getString("creds_secret_$tracker", "").get()
            if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
                Pair(clientId, clientSecret)
            } else null
        }
    }

    /**
     * Remove stored OAuth client credentials for a tracker.
     */
    fun removeClientCredentials(tracker: String) {
        if (useKeychain) {
            keychain!!.delete("creds_id_$tracker")
            keychain!!.delete("creds_secret_$tracker")
            logger.info { "OAuth client credentials removed for $tracker (Keychain)" }
        } else {
            preferenceStore.getString("creds_id_$tracker", "").delete()
            preferenceStore.getString("creds_secret_$tracker", "").delete()
            logger.info { "OAuth client credentials removed for $tracker (preferences)" }
        }
    }

    /**
     * Remove all stored tokens (logout all).
     */
    fun removeAll() {
        knownTrackers.forEach { removeTokens(it) }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private fun key(tracker: String) = "tracker_token_$tracker"

    private fun displayName(tracker: String): String = when (tracker) {
        "myanimelist" -> "MyAnimeList"
        "anilist" -> "AniList"
        "kitsu" -> "Kitsu"
        "shikimori" -> "Shikimori"
        else -> tracker
    }

    /**
     * Retrieve tokens from Keychain. Decodes the JSON blob stored by [saveTokens].
     */
    private fun getTokensFromKeychain(tracker: String): StoredToken? {
        val raw = getRawTokenFromKeychain(tracker) ?: return null
        // Parse the JSON blob to extract access + refresh tokens
        val blob = try {
            json.decodeFromString<StoredTokenBlob>(raw)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to decode Keychain token blob for $tracker" }
            return null
        }
        // Load metadata from preferences
        val meta = getTokenMetadata(tracker)
        return StoredToken(
            accessToken = blob.accessToken,
            refreshToken = blob.refreshToken,
            tokenType = meta?.tokenType ?: "Bearer",
            expiresIn = meta?.expiresIn ?: 3600,
            scope = meta?.scope ?: "",
            createdAt = meta?.createdAt ?: System.currentTimeMillis() / 1000,
            username = getUsername(tracker),
        )
    }

    /**
     * Get the raw token blob from Keychain (without decoding).
     */
    private fun getRawTokenFromKeychain(tracker: String): String? {
        return keychain?.retrieve("tracker_token_$tracker")
    }

    /**
     * Retrieve tokens from preferences.json (legacy fallback).
     */
    private fun getTokensFromPrefs(tracker: String): StoredToken? {
        val raw = preferenceStore.getString(key(tracker), "").get()
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString<StoredToken>(raw)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to decode stored token for $tracker from prefs" }
            null
        }
    }

    /**
     * Load token metadata from preferences.
     */
    private fun getTokenMetadata(tracker: String): TokenMetadata? {
        val raw = preferenceStore.getString("tracker_meta_$tracker", "").get()
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString<TokenMetadata>(raw)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to decode token metadata for $tracker" }
            null
        }
    }

    /**
     * Serialisable token storage for persistence (legacy format, all-in-one prefs).
     */
    @Serializable
    data class StoredToken(
        val accessToken: String,
        val refreshToken: String,
        val tokenType: String = "Bearer",
        val expiresIn: Long = 3600,
        val scope: String = "",
        val createdAt: Long = System.currentTimeMillis() / 1000,
        val username: String? = null,
    )
}

/**
 * Minimal token blob stored in the macOS Keychain.
 * Only contains the sensitive fields (access + refresh tokens).
 * Non-sensitive metadata is stored separately in preferences.json.
 */
@Serializable
data class StoredTokenBlob(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Non-sensitive token metadata stored in preferences.json.
 * Kept separate from the secrets so metadata lookups don't require Keychain access.
 */
@Serializable
data class TokenMetadata(
    val tokenType: String = "Bearer",
    val expiresIn: Long = 3600,
    val scope: String = "",
    val createdAt: Long = System.currentTimeMillis() / 1000,
)
