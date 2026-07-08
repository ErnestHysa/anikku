package app.anikku.macos.platform.auth

import app.anikku.macos.platform.preference.MacOSPreferenceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
                        JSONObject(body).optString("name", null)
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
                            ?.optString("name", null)
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
