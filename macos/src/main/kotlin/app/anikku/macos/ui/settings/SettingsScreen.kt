package app.anikku.macos.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anikku.macos.ui.components.CheckboxItem
import app.anikku.macos.ui.components.HeadingItem
import app.anikku.macos.ui.components.SelectItem
import app.anikku.macos.ui.theme.AnikkuTheme

/**
 * Settings screen — Phase 5 v1.
 *
 * Replaces the MoreScreen placeholder with a functional settings UI.
 * Provides:
 * - Appearance section: theme selector (18+ color schemes), AMOLED black toggle
 * - About section: app version, build info
 *
 * Preferences are read/written through [SettingsState] via [LocalSettingsState].
 * The theme changes take effect immediately because [AnikkuTheme] reads from
 * the same state holder.
 */
@Composable
fun SettingsScreen() {
    val settings = LocalSettingsState.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 80.dp),
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        )

        HorizontalDivider()

        // -----------------------------------------------------------------------
        // Appearance
        // -----------------------------------------------------------------------
        HeadingItem("Appearance")

        // Theme selector
        val themeNames = remember { AnikkuTheme.allThemes.map { it.displayName }.toTypedArray() }
        var themeIndex by remember(settings.theme) {
            mutableStateOf(AnikkuTheme.allThemes.indexOf(settings.theme).coerceAtLeast(0))
        }
        SelectItem(
            label = "Theme",
            options = themeNames,
            selectedIndex = themeIndex,
            onSelect = { index ->
                settings.theme = AnikkuTheme.allThemes[index]
            },
        )

        // AMOLED black toggle
        var amoled by remember(settings.isAmoledOLED) { mutableStateOf(settings.isAmoledOLED) }
        CheckboxItem(
            label = "AMOLED black",
            checked = amoled,
            onClick = {
                amoled = !amoled
                settings.isAmoledOLED = amoled
            },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // -----------------------------------------------------------------------
        // About
        // -----------------------------------------------------------------------
        HeadingItem("About")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Anikku macOS",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A native macOS anime watching application, " +
                    "ported from the Anikku Android app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
