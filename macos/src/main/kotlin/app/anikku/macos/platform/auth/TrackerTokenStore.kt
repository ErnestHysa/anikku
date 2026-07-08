package app.anikku.macos.platform.auth

import app.anikku.macos.platform.preference.MacOSPreferenceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Persists OAuth tokens for tracker services using [MacOSPreferenceStore].
 *
 * Each tracker (myanimelist, anilist, kitsu, shikimori) stores its tokens
 * under a key prefixed with `tracker_token_<name>`.
 *
 * ## Usage
 *
 * ```kotlin
 * val tokenStore = TrackerTokenStore(preferenceStore)
 * val tokens = tokenStore.getTokens("myanimelist")
 * tokenStore.saveTokens("myanimelist", TokenResponse(...))
 * tokenStore.removeTokens("myanimelist")
 * ```
 */
class TrackerTokenStore(
    private val preferenceStore: MacOSPreferenceStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

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
        val tokens = getTokens(tracker)
        TrackerLoginStatus(
            tracker = tracker,
            displayName = displayName(tracker),
            isLoggedIn = tokens != null,
            username = getUsername(tracker),
        )
    }

    /**
     * Check if a tracker has stored tokens.
     */
    fun isLoggedIn(tracker: String): Boolean = getTokens(tracker) != null

    /**
     * Load stored tokens for a tracker.
     */
    fun getTokens(tracker: String): StoredToken? {
        val raw = preferenceStore.getString(key(tracker), "").get()
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString<StoredToken>(raw)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to decode stored token for $tracker" }
            null
        }
    }

    /**
     * Save tokens for a tracker.
     */
    fun saveTokens(tracker: String, token: TokenResponse) {
        val stored = StoredToken(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            tokenType = token.tokenType,
            expiresIn = token.expiresIn,
            scope = token.scope,
            createdAt = token.createdAt,
        )
        preferenceStore.getString(key(tracker), "").set(json.encodeToString(stored))
        logger.info { "Tokens saved for $tracker" }
    }

    /**
     * Save tokens with username from a successful validation.
     */
    fun saveTokensWithUsername(tracker: String, token: TokenResponse, username: String) {
        val stored = StoredToken(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            tokenType = token.tokenType,
            expiresIn = token.expiresIn,
            scope = token.scope,
            createdAt = token.createdAt,
            username = username,
        )
        preferenceStore.getString(key(tracker), "").set(json.encodeToString(stored))
        logger.info { "Tokens saved for $tracker (user: $username)" }
    }

    /**
     * Get the stored username for a tracker, if available.
     */
    fun getUsername(tracker: String): String? = getTokens(tracker)?.username

    /**
     * Remove stored tokens for a tracker (logout).
     */
    fun removeTokens(tracker: String) {
        preferenceStore.getString(key(tracker), "").delete()
        logger.info { "Tokens removed for $tracker" }
    }

    /**
     * Remove all stored tokens (logout all).
     */
    fun removeAll() {
        knownTrackers.forEach { removeTokens(it) }
    }

    private fun key(tracker: String) = "tracker_token_$tracker"

    private fun displayName(tracker: String): String = when (tracker) {
        "myanimelist" -> "MyAnimeList"
        "anilist" -> "AniList"
        "kitsu" -> "Kitsu"
        "shikimori" -> "Shikimori"
        else -> tracker
    }

    /**
     * Serialisable token storage for persistence.
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
