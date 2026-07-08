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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import app.anikku.macos.platform.web.BrowserLauncher

/**
 * About dialog for Anikku macOS.
 *
 * Displays app name, version, description, and a clickable link to the
 * GitHub repository. The "Open GitHub" button launches the system browser
 * via [BrowserLauncher.openSafe].
 *
 * Shown as a lightweight dialog window — centered on the parent window.
 *
 * @param onCloseRequest Called when the user wants to close the dialog.
 */
@Composable
fun AboutDialog(
    onCloseRequest: () -> Unit,
) {
    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "About Anikku",
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            width = 400.dp,
            height = 320.dp,
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

                Spacer(Modifier.height(20.dp))

                // GitHub link button — opens system browser via BrowserLauncher
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

                Spacer(Modifier.height(16.dp))

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
