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
 * Settings screen — Phase 5 expanded.
 *
 * Provides multiple preference categories:
 * - Appearance: theme selector (18+ color schemes), AMOLED black toggle
 * - Library: badges, tabs preferences
 * - Player: default player behavior
 * - Tracking: tracker login (placeholder)
 * - About: app version, build info
 *
 * Preferences are read/written through [SettingsState] via [LocalSettingsState].
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

        // =====================================================================
        // Appearance
        // =====================================================================
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

        // =====================================================================
        // Library
        // =====================================================================
        HeadingItem("Library")

        var showCategoryTabs by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Show category tabs",
            checked = showCategoryTabs,
            onClick = { showCategoryTabs = !showCategoryTabs },
        )

        var showEpisodeCount by remember { mutableStateOf(false) }
        CheckboxItem(
            label = "Show number of items",
            checked = showEpisodeCount,
            onClick = { showEpisodeCount = !showEpisodeCount },
        )

        var downloadBadge by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Show download badge",
            checked = downloadBadge,
            onClick = { downloadBadge = !downloadBadge },
        )

        var localBadge by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Show local badge",
            checked = localBadge,
            onClick = { localBadge = !localBadge },
        )

        var languageBadge by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Show language badge",
            checked = languageBadge,
            onClick = { languageBadge = !languageBadge },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Player
        // =====================================================================
        HeadingItem("Player")

        var autoPlay by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Auto-play next episode",
            checked = autoPlay,
            onClick = { autoPlay = !autoPlay },
        )

        var resumeFromLast by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Resume from last position",
            checked = resumeFromLast,
            onClick = { resumeFromLast = !resumeFromLast },
        )

        var skipIntro by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Skip intro (when available)",
            checked = skipIntro,
            onClick = { skipIntro = !skipIntro },
        )

        val playbackSpeedOptions = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        var speedIndex by remember { mutableStateOf(2) } // Default 1.0x
        SelectItem(
            label = "Default playback speed",
            options = playbackSpeedOptions,
            selectedIndex = speedIndex,
            onSelect = { speedIndex = it },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // Downloads
        // =====================================================================
        HeadingItem("Downloads")

        var downloadOnWifiOnly by remember { mutableStateOf(true) }
        CheckboxItem(
            label = "Download on Wi-Fi only",
            checked = downloadOnWifiOnly,
            onClick = { downloadOnWifiOnly = !downloadOnWifiOnly },
        )

        var simultaneousDownloads by remember { mutableStateOf(3) }
        SelectItem(
            label = "Simultaneous downloads",
            options = arrayOf("1", "2", "3", "4", "5"),
            selectedIndex = simultaneousDownloads - 1,
            onSelect = { simultaneousDownloads = it + 1 },
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // =====================================================================
        // About
        // =====================================================================
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = "https://github.com/komikku-app/anikku",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
