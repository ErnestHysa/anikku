package app.anikku.macos.platform.auth

import androidx.compose.runtime.compositionLocalOf
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val logger = KotlinLogging.logger {}

/**
 * High-level manager for tracker authentication and token lifecycle.
 *
 * Orchestrates the full OAuth flow:
 * 1. Opens system browser for authorization
 * 2. Starts local callback server
 * 3. Exchanges authorization code for tokens
 * 4. Persists tokens via [TrackerTokenStore]
 * 5. Validates/refreshes tokens on app start
 *
 * ## Usage
 *
 * ```kotlin
 * val manager = TrackerManager(networkHelper.client, preferenceStore)
 *
 * // Get login status
 * val statuses = manager.getLoginStatuses()
 *
 * // Login to MyAnimeList
 * manager.login("myanimelist", "client_id", "client_secret")
 *
 * // Logout
 * manager.logout("myanimelist")
 * ```
 */
class TrackerManager(
    private val oauthManager: TrackerOAuthManager,
    private val tokenStore: TrackerTokenStore,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** Observable login status for all trackers. */
    private val _loginStatuses = MutableStateFlow(tokenStore.getAllStatuses())
    val loginStatuses: StateFlow<List<TrackerTokenStore.TrackerLoginStatus>> =
        _loginStatuses.asStateFlow()

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Refresh the login status from stored tokens.
     */
    fun refreshStatus() {
        _loginStatuses.value = tokenStore.getAllStatuses()
    }

    /**
     * Perform the full OAuth login flow for a tracker.
     *
     * @param tracker The tracker name (e.g., "myanimelist", "anilist").
     * @param clientId OAuth client ID.
     * @param clientSecret OAuth client secret.
     * @param onResult Called on the main thread with the result message.
     */
    fun login(
        tracker: String,
        clientId: String,
        clientSecret: String,
        onResult: (Boolean, String) -> Unit,
    ) {
        scope.launch {
            try {
                logger.info { "Starting OAuth login for $tracker..." }
                val token = oauthManager.completeLogin(
                    tracker = tracker,
                    clientId = clientId,
                    clientSecret = clientSecret,
                )

                if (token != null) {
                    // Validate and get username
                    val username = lookupUsername(tracker, token.accessToken)
                    tokenStore.saveTokensWithUsername(tracker, token, username ?: tracker)
                    refreshStatus()
                    logger.info { "OAuth login successful for $tracker (user: $username)" }
                    onResult(true, "Logged in to $tracker${if (username != null) " as $username" else ""}")
                } else {
                    logger.warn { "OAuth login failed for $tracker — no token returned" }
                    onResult(false, "Authentication failed or timed out")
                }
            } catch (e: Exception) {
                logger.error(e) { "OAuth login error for $tracker" }
                onResult(false, "Error: ${e.message?.take(100) ?: "Unknown error"}")
            }
        }
    }

    /**
     * Logout from a tracker by removing stored tokens.
     */
    fun logout(tracker: String) {
        tokenStore.removeTokens(tracker)
        refreshStatus()
    }

    /**
     * Check if the user is logged in to a tracker.
     */
    fun isLoggedIn(tracker: String): Boolean = tokenStore.isLoggedIn(tracker)

    /**
     * Get the username for a logged-in tracker.
     */
    fun getUsername(tracker: String): String? = tokenStore.getUsername(tracker)

    /**
     * Search a tracker for an anime by title.
     *
     * **Note:** This method performs synchronous network I/O. Call it from a
     * background thread (e.g., inside [scrobbleProgress]) to avoid blocking
     * the UI.
     *
     * @param tracker The tracker name (e.g., "myanimelist", "anilist").
     * @param query The anime title to search for.
     * @return A list of search results, or an empty list if not logged in or on error.
     */
    fun searchAnime(tracker: String, query: String): List<TrackerSearchResult> {
        val token = tokenStore.getTokens(tracker)?.accessToken ?: return emptyList()

        return try {
            when (tracker) {
                "myanimelist" -> searchMyAnimeList(token, query)
                "anilist" -> searchAniList(token, query)
                else -> emptyList()
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to search $tracker for \"$query\"" }
            emptyList()
        }
    }

    /**
     * Update the watched progress for a specific anime on a tracker.
     *
     * **Note:** This method performs synchronous network I/O. Call it from a
     * background thread (e.g., inside [scrobbleProgress]) to avoid blocking
     * the UI.
     *
     * @param tracker The tracker name (e.g., "myanimelist", "anilist").
     * @param remoteAnimeId The tracker's anime ID.
     * @param episodeNumber The number of episodes watched.
     * @param status Optional status override (e.g., "watching", "completed").
     * @return true if the update was accepted by the API.
     */
    fun updateProgress(
        tracker: String,
        remoteAnimeId: String,
        episodeNumber: Int,
        status: String? = null,
    ): Boolean {
        val token = tokenStore.getTokens(tracker)?.accessToken ?: return false

        return try {
            when (tracker) {
                "myanimelist" -> updateMyAnimeList(token, remoteAnimeId, episodeNumber, status)
                "anilist" -> updateAniList(token, remoteAnimeId, episodeNumber, status)
                else -> false
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to update $tracker progress for anime $remoteAnimeId" }
            false
        }
    }

    /**
     * Fire-and-forget scrobble for an anime across all logged-in trackers.
     * Searches for the anime by title and updates the progress to [episodeNumber].
     *
     * @param animeTitle The anime title to search for.
     * @param episodeNumber The episode number to report.
     */
    fun scrobbleProgress(animeTitle: String, episodeNumber: Int) {
        if (animeTitle.isBlank()) return
        scope.launch(Dispatchers.IO) {
            tokenStore.getAllStatuses()
                .filter { it.isLoggedIn }
                .forEach { status ->
                    try {
                        val results = searchAnime(status.tracker, animeTitle)
                        val bestMatch = results.firstOrNull() ?: return@forEach
                        updateProgress(status.tracker, bestMatch.id, episodeNumber)
                        logger.info { "Scrobbled \"$animeTitle\" ep $episodeNumber to ${status.tracker} (id=${bestMatch.id})" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to scrobble \"$animeTitle\" to ${status.tracker}" }
                    }
                }
        }
    }

    // -----------------------------------------------------------------------
    // Internal tracker implementations
    // -----------------------------------------------------------------------

    private fun searchMyAnimeList(token: String, query: String): List<TrackerSearchResult> {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.myanimelist.net")
            .addPathSegments("v2/anime")
            .addQueryParameter("q", query.take(64))
            .addQueryParameter("limit", "5")
            .addQueryParameter("fields", "id,title,main_picture")
            .build()

        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val data = JSONObject(response.body?.string() ?: "").optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).map { i ->
            val node = data.getJSONObject(i).getJSONObject("node")
            TrackerSearchResult(
                id = node.getInt("id").toString(),
                title = node.getString("title"),
                imageUrl = node.optJSONObject("main_picture")?.optString("medium"),
            )
        }
    }

    private fun searchAniList(token: String, query: String): List<TrackerSearchResult> {
        val escapedQuery = JSONObject.quote(query)
        val gql = """
            {"query":"query Search(${'$'}q: String) { Page(perPage: 5) { media(search: ${'$'}q, type: ANIME) { id title { romaji english } coverImage { medium } } } }","variables":{"q":$escapedQuery}}
        """.trimIndent()

        val request = okhttp3.Request.Builder()
            .url("https://graphql.anilist.co")
            .header("Authorization", "Bearer $token")
            .post(gql.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val media = JSONObject(response.body?.string() ?: "")
            .optJSONObject("data")
            ?.optJSONObject("Page")
            ?.optJSONArray("media") ?: return emptyList()

        return (0 until media.length()).map { i ->
            val node = media.getJSONObject(i)
            val titleObj = node.optJSONObject("title")
            val title = titleObj?.optString("romaji")
                ?: titleObj?.optString("english")
                ?: node.getString("id")
            TrackerSearchResult(
                id = node.getInt("id").toString(),
                title = title,
                imageUrl = node.optJSONObject("coverImage")?.optString("medium"),
            )
        }
    }

    private fun updateMyAnimeList(
        token: String,
        remoteAnimeId: String,
        episodeNumber: Int,
        status: String?,
    ): Boolean {
        val formBody = FormBody.Builder()
            .add("num_watched_episodes", episodeNumber.coerceAtLeast(0).toString())
        status?.let { formBody.add("status", it) }

        val request = okhttp3.Request.Builder()
            .url("https://api.myanimelist.net/v2/anime/$remoteAnimeId/my_list_status")
            .header("Authorization", "Bearer $token")
            .patch(formBody.build())
            .build()

        val response = httpClient.newCall(request).execute()
        response.close()
        return response.isSuccessful
    }

    private fun updateAniList(
        token: String,
        remoteAnimeId: String,
        episodeNumber: Int,
        status: String?,
    ): Boolean {
        val alStatus = when (status) {
            "watching" -> "CURRENT"
            "completed" -> "COMPLETED"
            "on_hold" -> "PAUSED"
            "dropped" -> "DROPPED"
            "plan_to_watch" -> "PLANNING"
            else -> null
        }

        val variables = JSONObject().apply {
            put("mediaId", remoteAnimeId.toIntOrNull() ?: return false)
            put("progress", episodeNumber.coerceAtLeast(0))
            if (alStatus != null) put("status", alStatus)
        }

        val statusDeclaration = if (alStatus != null) ", ${'$'}status: MediaListStatus" else ""
        val statusArgument = if (alStatus != null) ", status: ${'$'}status" else ""
        val query = "mutation Save(${'$'}mediaId: Int, ${'$'}progress: Int$statusDeclaration) { SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress$statusArgument) { id progress } }"

        val payload = JSONObject().apply {
            put("query", query)
            put("variables", variables)
        }

        val request = okhttp3.Request.Builder()
            .url("https://graphql.anilist.co")
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val response = httpClient.newCall(request).execute()
        response.close()
        return response.isSuccessful
    }

    /**
     * Validate and optionally refresh tokens for all trackers.
     * Called on app startup.
     */
    fun validateAllTokens() {
        tokenStore.getAllStatuses().filter { it.isLoggedIn }.forEach { status ->
            val stored = tokenStore.getTokens(status.tracker) ?: return@forEach
            if (stored.accessToken.isNotEmpty()) {
                val isValid = oauthManager.validateToken(status.tracker, stored.accessToken)
                if (!isValid && stored.refreshToken.isNotEmpty()) {
                    scope.launch {
                        try {
                            val clientId = "" // Would need stored client ID
                            val clientSecret = "" // Would need stored client secret
                            val refreshed = oauthManager.refreshToken(
                                tracker = status.tracker,
                                refreshToken = stored.refreshToken,
                                clientId = clientId,
                                clientSecret = clientSecret,
                            )
                            if (refreshed != null) {
                                tokenStore.saveTokens(status.tracker, refreshed)
                                logger.info { "Token refreshed for ${status.tracker}" }
                            } else {
                                logger.warn { "Token refresh failed for ${status.tracker}" }
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "Token refresh error for ${status.tracker}" }
                        }
                        refreshStatus()
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    /**
     * Look up the username for a tracker using a valid access token.
     */
    private fun lookupUsername(tracker: String, accessToken: String): String? {
        return try {
            when (tracker) {
                "myanimelist" -> {
                    val request = okhttp3.Request.Builder()
                        .url("https://api.myanimelist.net/v2/users/@me")
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return null
                        JSONObject(body).optString("name")
                    } else null
                }
                "anilist" -> {
                    val query = "{\"query\":\"query { Viewer { name } }\"}"
                    val request = okhttp3.Request.Builder()
                        .url("https://graphql.anilist.co")
                        .header("Authorization", "Bearer $accessToken")
                        .post(query.toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return null
                        JSONObject(body)
                            .optJSONObject("data")
                            ?.optJSONObject("Viewer")
                            ?.optString("name")
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to lookup username for $tracker" }
            null
        }
    }
}

/**
 * Search result returned by a tracker search.
 */
data class TrackerSearchResult(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
)

/**
 * CompositionLocal for [TrackerManager] — used in SettingsScreen
 * to provide tracker login/logout functionality.
 *
 * Must be provided via [CompositionLocalProvider] in AnikkuApp.kt:
 * ```kotlin
 * val trackerManager = TrackerManager(oauthManager, tokenStore, httpClient)
 * CompositionLocalProvider(LocalTrackerManager provides trackerManager) { ... }
 * ```
 */
val LocalTrackerManager = compositionLocalOf<TrackerManager?> { null }
