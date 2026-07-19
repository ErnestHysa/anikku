package app.anikku.macos.platform.auth

import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSKeychain
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

class TrackerTokenStore(
    private val preferenceStore: MacOSPreferenceStore,
    private val keychain: MacOSKeychain? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val useKeychain: Boolean get() = keychain != null && keychain.isAvailable
    private val knownTrackers = listOf("myanimelist", "anilist", "kitsu", "shikimori")

    data class TrackerLoginStatus(
        val tracker: String,
        val displayName: String,
        val isLoggedIn: Boolean,
        val username: String? = null,
    )

    fun getAllStatuses(): List<TrackerLoginStatus> = knownTrackers.map { tracker ->
        TrackerLoginStatus(
            tracker = tracker,
            displayName = displayName(tracker),
            isLoggedIn = getTokens(tracker) != null,
            username = getUsername(tracker),
        )
    }

    fun isLoggedIn(tracker: String): Boolean {
        return if (useKeychain) {
            getRawTokenFromKeychain(tracker) != null
        } else {
            getTokensFromPrefs(tracker) != null
        }
    }

    fun getTokens(tracker: String): StoredToken? {
        return if (useKeychain) {
            getTokensFromKeychain(tracker)
        } else {
            getTokensFromPrefs(tracker)
        }
    }

    fun saveTokens(tracker: String, token: TokenResponse) {
        val metadata = TokenMetadata(
            tokenType = token.tokenType,
            expiresIn = token.expiresIn,
            scope = token.scope,
            createdAt = token.createdAt,
        )
        if (useKeychain) {
            val tokenBlob = json.encodeToString(StoredTokenBlob(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
            ))
            keychain!!.store("tracker_token_$tracker", tokenBlob)
            preferenceStore.getString("tracker_meta_$tracker", "").set(json.encodeToString(metadata))
            logger.info { "Tokens saved for $tracker (Keychain)" }
        } else {
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

    fun saveTokensWithUsername(tracker: String, token: TokenResponse, username: String) {
        saveTokens(tracker, token)
        preferenceStore.getString("tracker_user_$tracker", "").set(username)
        logger.info { "Username saved for $tracker: $username" }
    }

    fun getUsername(tracker: String): String? {
        val raw = preferenceStore.getString("tracker_user_$tracker", "").get()
        return raw.ifBlank { null }
    }

    fun removeTokens(tracker: String) {
        if (useKeychain) {
            keychain!!.delete("tracker_token_$tracker")
            logger.info { "Tokens removed for $tracker (Keychain)" }
        } else {
            preferenceStore.getString(key(tracker), "").delete()
            logger.info { "Tokens removed for $tracker (preferences)" }
        }
        preferenceStore.getString("tracker_meta_$tracker", "").delete()
        preferenceStore.getString("tracker_user_$tracker", "").delete()
    }

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

    fun saveAnimeMapping(tracker: String, animeTitle: String, trackerAnimeId: String) {
        val key = "tracker_map_${tracker}_${animeTitle.hashCode()}"
        val mapping = AnimeMapping(animeTitle = animeTitle, trackerAnimeId = trackerAnimeId)
        preferenceStore.getString(key, "").set(json.encodeToString(mapping))
        logger.info { "Anime mapping saved for \"${animeTitle.take(30)}\" on $tracker -> $trackerAnimeId" }
    }

    fun getAnimeMapping(tracker: String, animeTitle: String): String? {
        val key = "tracker_map_${tracker}_${animeTitle.hashCode()}"
        val raw = preferenceStore.getString(key, "").get()
        if (raw.isBlank()) return null
        return try {
            val mapping = json.decodeFromString<AnimeMapping>(raw)
            mapping.trackerAnimeId
        } catch (e: Exception) {
            logger.warn(e) { "Failed to decode anime mapping for $tracker" }
            null
        }
    }

    fun removeAnimeMapping(tracker: String, animeTitle: String) {
        val key = "tracker_map_${tracker}_${animeTitle.hashCode()}"
        preferenceStore.getString(key, "").delete()
        logger.info { "Anime mapping removed for \"${animeTitle.take(30)}\" on $tracker" }
    }

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

    private fun getTokensFromKeychain(tracker: String): StoredToken? {
        val raw = getRawTokenFromKeychain(tracker) ?: return null
        val blob = try {
            json.decodeFromString<StoredTokenBlob>(raw)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to decode Keychain token blob for $tracker" }
            return null
        }
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

    private fun getRawTokenFromKeychain(tracker: String): String? {
        return keychain?.retrieve("tracker_token_$tracker")
    }

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

@Serializable
data class StoredTokenBlob(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class AnimeMapping(
    val animeTitle: String,
    val trackerAnimeId: String,
)

@Serializable
data class TokenMetadata(
    val tokenType: String = "Bearer",
    val expiresIn: Long = 3600,
    val scope: String = "",
    val createdAt: Long = System.currentTimeMillis() / 1000,
)
