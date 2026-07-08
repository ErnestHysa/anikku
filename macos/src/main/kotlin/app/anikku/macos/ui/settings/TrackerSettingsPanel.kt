package app.anikku.macos.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.auth.TrackerManager
import app.anikku.macos.platform.auth.TrackerTokenStore
import app.anikku.macos.ui.components.HeadingItem
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import kotlinx.coroutines.launch

/**
 * OAuth client credentials for tracker services.
 *
 * Defaults are empty — users must provide their own credentials
 * by registering an app with each tracker service.
 *
 * - **MyAnimeList**: https://myanimelist.net/apiconfig
 * - **AniList**: https://anilist.co/settings/developer
 * - **Kitsu**: https://kitsu.io/settings/apps
 * - **Shikimori**: https://shikimori.one/oauth/apps
 */
data class TrackerCredentials(
    val clientId: String = "",
    val clientSecret: String = "",
)

/**
 * Tracker settings panel — login/logout for each supported tracker.
 *
 * Displays a card for each tracker service with:
 * - Tracker name and login status
 * - Login button (starts OAuth flow via system browser)
 * - Logout button (removes stored token)
 *
 * @param trackerManager The [TrackerManager] handling OAuth flows.
 * @param onLoginStart Called when login starts (tracker name passed).
 * @param onLoginResult Called when login completes (tracker name, success, message).
 */
@Composable
fun TrackerSettingsPanel(
    trackerManager: TrackerManager?,
    myanimelistCredentials: TrackerCredentials = TrackerCredentials(),
    anilistCredentials: TrackerCredentials = TrackerCredentials(),
    onTrackerChanged: () -> Unit = {},
) {
    val toastHost = LocalToastHost.current
    val scope = rememberCoroutineScope()
    var loggingInTracker by remember { mutableStateOf<String?>(null) }

    // Collect login statuses reactively
    val statuses by remember(trackerManager) {
        trackerManager?.loginStatuses ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    HeadingItem("Tracking")

    statuses.forEach { status ->
        TrackerCard(
            tracker = status.tracker,
            displayName = status.displayName,
            isLoggedIn = status.isLoggedIn,
            username = status.username,
            isLoggingIn = loggingInTracker == status.tracker,
            onLogin = {
                if (trackerManager == null) {
                    toastHost.show("Tracker manager not available", ToastDuration.SHORT)
                    return@TrackerCard
                }

                val creds = when (status.tracker) {
                    "myanimelist" -> myanimelistCredentials
                    "anilist" -> anilistCredentials
                    else -> TrackerCredentials()
                }

                if (creds.clientId.isBlank()) {
                    toastHost.show("${status.displayName}: Set client ID in code or use a proxy", ToastDuration.LONG)
                    return@TrackerCard
                }

                loggingInTracker = status.tracker
                toastHost.show("Opening browser for ${status.displayName} login...", ToastDuration.LONG)

                trackerManager.login(
                    tracker = status.tracker,
                    clientId = creds.clientId,
                    clientSecret = creds.clientSecret,
                ) { success, message ->
                    loggingInTracker = null
                    toastHost.show(message, ToastDuration.SHORT)
                    if (success) onTrackerChanged()
                }
            },
            onLogout = {
                trackerManager?.logout(status.tracker)
                toastHost.show("Logged out of ${status.displayName}", ToastDuration.SHORT)
                onTrackerChanged()
            },
        )
    }

    if (statuses.isEmpty()) {
        Text(
            text = "No trackers available",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Info text about credentials
    Text(
        text = "To use tracker sync, register an app with each service and set the client ID/secret.",
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun TrackerCard(
    tracker: String,
    displayName: String,
    isLoggedIn: Boolean,
    username: String?,
    isLoggingIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = when {
                        isLoggingIn -> "Logging in..."
                        isLoggedIn -> "Logged in${if (username != null) " as $username" else ""}"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isLoggingIn -> MaterialTheme.colorScheme.tertiary
                        isLoggedIn -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.width(8.dp))

            if (isLoggedIn) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !isLoggingIn,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Logout", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Button(
                    onClick = onLogin,
                    enabled = !isLoggingIn,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Icon(Icons.Outlined.Login, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Login", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
