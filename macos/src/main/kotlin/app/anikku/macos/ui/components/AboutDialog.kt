package app.anikku.macos.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import app.anikku.macos.platform.update.AppUpdateChecker
import app.anikku.macos.platform.update.SparkleUpdater
import app.anikku.macos.platform.update.UpdateInfo
import app.anikku.macos.platform.web.BrowserLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * About dialog for Anikku macOS.
 *
 * Displays app name, version, description, and a clickable link to the
 * GitHub repository. Includes a "Check for Updates" button that tries
 * [SparkleUpdater] first (for native macOS auto-update), then falls back
 * to the GitHub Releases API via [AppUpdateChecker].
 *
 * @param onCloseRequest Called when the user wants to close the dialog.
 * @param updateChecker The AppUpdateChecker instance to use for update checks.
 *                      If null, the check button is disabled.
 * @param sparkleUpdater Optional Sparkle auto-updater. When available, used
 *                       as the primary update mechanism.
 * @param autoCheck If true, automatically starts the update check when the
 *                  dialog opens (used when triggered from menu bar).
 */
@Composable
fun AboutDialog(
    onCloseRequest: () -> Unit,
    updateChecker: AppUpdateChecker? = null,
    sparkleUpdater: SparkleUpdater? = null,
    autoCheck: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var updateState by remember {
        mutableStateOf<UpdateState>(
            if (autoCheck && (sparkleUpdater != null || updateChecker != null))
                UpdateState.Checking else UpdateState.Idle,
        )
    }

    // Auto-trigger the update check when dialog is opened from menu bar
    if (autoCheck && (sparkleUpdater != null || updateChecker != null)) {
        LaunchedEffect(Unit) {
            val foundUpdate = withContext(Dispatchers.IO) {
                // Try Sparkle first (only when natively available)
                if (sparkleUpdater != null && sparkleUpdater.isAvailable) {
                    sparkleUpdater.checkForUpdatesWithUI()
                    null // Sparkle handles its own UI — no UpdateInfo needed
                } else {
                    updateChecker?.checkForUpdateSync()
                }
            }
            updateState = if (foundUpdate != null) {
                UpdateState.Available(foundUpdate)
            } else {
                UpdateState.UpToDate
            }
        }
    }

    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "About Anikku",
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            width = 420.dp,
            height = 400.dp,
        ),
        resizable = false,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // App icon / emblem
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(16.dp))

                // App name
                Text(
                    text = "Anikku",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(4.dp))

                // Suffix and version
                Text(
                    text = "macOS",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Version 1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(Modifier.height(12.dp))

                // Description
                Text(
                    text = "A native macOS anime watching application, " +
                        "ported from the Anikku Android app. " +
                        "Browse, stream, and manage your anime library " +
                        "with tracker sync and mpv-powered playback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                // ── Update Check Section ─────────────────────────────────
                when (val state = updateState) {
                    is UpdateState.Idle -> {
                        val hasUpdater = sparkleUpdater != null || updateChecker != null
                        if (hasUpdater) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        updateState = UpdateState.Checking

                                        // Try Sparkle first (only when natively available)
                                        if (sparkleUpdater != null && sparkleUpdater.isAvailable) {
                                            withContext(Dispatchers.IO) {
                                                sparkleUpdater.checkForUpdatesWithUI()
                                            }
                                            // Sparkle native UI takes over — no UpdateInfo returned
                                            updateState = UpdateState.UpToDate
                                        } else {
                                            val update = withContext(Dispatchers.IO) {
                                                updateChecker?.checkForUpdateSync()
                                            }
                                            updateState = if (update != null) {
                                                UpdateState.Available(update)
                                            } else {
                                                UpdateState.UpToDate
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text("Check for Updates")
                            }
                        } else {
                            Text(
                                text = "Auto-update not available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is UpdateState.Checking -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(0.6f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Checking for updates...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is UpdateState.Available -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Update Available!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.update.tagName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    BrowserLauncher.openSafe(state.update.downloadUrl)
                                },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Download Update")
                            }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    updateState = UpdateState.Idle
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }

                    is UpdateState.UpToDate -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Anikku is up to date!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { updateState = UpdateState.Idle },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text("Check Again")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(Modifier.height(12.dp))

                // GitHub link button
                OutlinedButton(
                    onClick = {
                        BrowserLauncher.openSafe("https://github.com/komikku-app/anikku")
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("Open GitHub Repository")
                }

                Spacer(Modifier.height(12.dp))

                // Close button
                Button(
                    onClick = onCloseRequest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(120.dp),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Internal state for the update check UI flow.
 */
private sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Available(val update: UpdateInfo) : UpdateState()
    data object UpToDate : UpdateState()
}
