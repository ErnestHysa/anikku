package app.anikku.macos.ui.screens.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.auth.TrackerTokenStore
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.settings.TrackerCredentials
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Possible states during the OAuth login flow.
 */
private enum class LoginState {
    /** Waiting for user action — show Connect button. */
    IDLE,
    /** OAuth flow in progress — browser opened, waiting for user to authorize. */
    AUTHORIZING,
    /** Login succeeded — show success animation. */
    SUCCESS,
    /** Login failed — show error with retry. */
    ERROR,
}

/**
 * Default credentials placeholder.
 * In production, users should register their own OAuth app with each tracker.
 * For built-in convenience, this can be populated with the app's registered credentials.
 */
object TrackerCredentialsProvider {
    /**
     * Default credentials for each tracker.
     * Override these by providing custom [TrackerCredentials] to the detail screen.
     */
    val defaults: Map<String, TrackerCredentials> = mapOf(
        "myanimelist" to TrackerCredentials(),
        "anilist" to TrackerCredentials(),
        "kitsu" to TrackerCredentials(),
        "shikimori" to TrackerCredentials(),
    )
}

/**
 * Tracker detail screen — OAuth login flow for a single tracker service.
 *
 * Shows a detailed view of the tracker with:
 * - Brand icon and name
 * - Login status and username
 * - OAuth flow with animated states (IDLE → AUTHORIZING → SUCCESS/ERROR)
 * - Connect and Disconnect buttons
 * - Info about how to register an OAuth app
 *
 * @param tracker The tracker name (e.g., "myanimelist", "anilist").
 * @param displayName The human-readable tracker name (e.g., "MyAnimeList").
 * @param credentials Optional OAuth client credentials. If empty, the user
 *                    will see instructions on how to register an app.
 */
data class TrackerDetailScreen(
    val tracker: String,
    val displayName: String,
    val credentials: TrackerCredentials? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    // Class-level state to survive Voyager backstack disposal
    private val _loginState = mutableStateOf(LoginState.IDLE)
    private val _errorMessage = mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val scope = rememberCoroutineScope()
        val trackerManager = LocalTrackerManager.current

        // Collect login status reactively to update on external changes
        val statuses by remember(trackerManager) {
            trackerManager?.loginStatuses ?: emptyFlow()
        }.collectAsState(initial = emptyList())

        val status = statuses.find { it.tracker == tracker }
        val isLoggedIn = status?.isLoggedIn == true
        val username = status?.username

        // Reset login state if logged in externally (e.g., from another screen)
        if (isLoggedIn && _loginState.value != LoginState.SUCCESS && _loginState.value != LoginState.IDLE) {
            _loginState.value = LoginState.SUCCESS
        }

        val brandColor = trackerBrandColor(tracker)

        // Tracker-specific OAuth app registration URL
        val registrationUrl = when (tracker) {
            "myanimelist" -> "https://myanimelist.net/apiconfig"
            "anilist" -> "https://anilist.co/settings/developer"
            "kitsu" -> "https://kitsu.io/settings/apps"
            "shikimori" -> "https://shikimori.one/oauth/apps"
            else -> null
        }

        val hasCredentials = credentials?.clientId?.isNotBlank() == true

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(displayName) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))

                // =====================================================================
                // Brand icon — large circle with initial
                // =====================================================================
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(brandColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayName.take(1),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = brandColor,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // =====================================================================
                // Tracker name
                // =====================================================================
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(4.dp))

                // =====================================================================
                // Login status
                // =====================================================================
                AnimatedContent(
                    targetState = if (isLoggedIn) "logged_in" else "not_logged_in",
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "status",
                ) { state ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (state == "logged_in") {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (username != null) "Connected as $username" else "Connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium,
                            )
                        } else {
                            Text(
                                text = "Not connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // =====================================================================
                // OAuth flow state — animated content
                // =====================================================================
                val loginState = _loginState.value

                AnimatedContent(
                    targetState = loginState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "login_flow",
                ) { state ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (state) {
                            LoginState.IDLE -> {
                                // ---- Not logged in ----
                                if (isLoggedIn) {
                                    // Already logged in — show disconnect
                                    Button(
                                        onClick = {
                                            trackerManager?.logout(tracker)
                                            _loginState.value = LoginState.IDLE
                                            toastHost.show("Logged out of $displayName", ToastDuration.SHORT)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Logout,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Disconnect $displayName",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                } else {
                                    // Connect button
                                    if (hasCredentials) {
                                        Button(
                                            onClick = {
                                                _loginState.value = LoginState.AUTHORIZING
                                                _errorMessage.value = null
                                                if (trackerManager != null) {
                                                    trackerManager.login(
                                                        tracker = tracker,
                                                        clientId = credentials!!.clientId,
                                                        clientSecret = credentials!!.clientSecret,
                                                    ) { success, message ->
                                                        scope.launch {
                                                            if (success) {
                                                                _loginState.value = LoginState.SUCCESS
                                                                toastHost.show(
                                                                    "Connected to $displayName",
                                                                    ToastDuration.SHORT,
                                                                )
                                                            } else {
                                                                _loginState.value = LoginState.ERROR
                                                                _errorMessage.value = message
                                                                toastHost.show(
                                                                    "$displayName: $message",
                                                                    ToastDuration.LONG,
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    _loginState.value = LoginState.ERROR
                                                    _errorMessage.value = "Tracker manager not available"
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = brandColor,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Launch,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Connect $displayName",
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    } else {
                                        // No credentials — show instruction card
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            ),
                                        ) {
                                            Column(modifier = Modifier.padding(20.dp)) {
                                                Text(
                                                    text = "OAuth Credentials Required",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = "To connect $displayName, you need to register a developer application and obtain OAuth client credentials:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.height(12.dp))
                                                Text(
                                                    text = "1. Go to the developer portal",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                Text(
                                                    text = "2. Create a new OAuth application",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                Text(
                                                    text = "3. Set the redirect URI to: http://127.0.0.1:0/callback",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                Text(
                                                    text = "4. Set the client ID and secret in the app",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                Spacer(Modifier.height(12.dp))
                                                if (registrationUrl != null) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            app.anikku.macos.platform.web.BrowserLauncher.openSafe(registrationUrl)
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.Launch,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            "Open ${displayName} Developer Portal",
                                                            style = MaterialTheme.typography.labelSmall,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            LoginState.AUTHORIZING -> {
                                // ---- Browser opened, waiting for user ----
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth(0.6f)
                                                .height(4.dp),
                                            color = brandColor,
                                        )
                                        Spacer(Modifier.height(20.dp))
                                        Text(
                                            text = "Waiting for authorization...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "A browser window has opened. Please sign in to $displayName and authorize Anikku.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        OutlinedButton(
                                            onClick = { _loginState.value = LoginState.IDLE },
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            LoginState.SUCCESS -> {
                                // ---- Login successful ----
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(64.dp),
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = "Connected Successfully!",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4CAF50),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = if (username != null) "Signed in as $username" else "Signed in to $displayName",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(24.dp))
                                        OutlinedButton(
                                            onClick = {
                                                trackerManager?.logout(tracker)
                                                _loginState.value = LoginState.IDLE
                                                toastHost.show("Logged out of $displayName", ToastDuration.SHORT)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Logout,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Disconnect",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }

                            LoginState.ERROR -> {
                                // ---- Login failed ----
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(56.dp),
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = "Connection Failed",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = _errorMessage.value ?: "An unknown error occurred.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(20.dp))
                                        Button(
                                            onClick = {
                                                _loginState.value = LoginState.IDLE
                                                _errorMessage.value = null
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Try Again", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // =====================================================================
                // Info card about OAuth data usage
                // =====================================================================
                if (loginState == LoginState.IDLE) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))

                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "What gets shared",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            BulletPoint("Your watch progress (episode numbers)")
                            BulletPoint("Anime titles you watch")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "We never share your email, password, or personal information.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


