# Anikku macOS

A native macOS anime watching application — a desktop port of the [Anikku](https://github.com/komikku-app/anikku) Android app, built with [Compose Multiplatform for Desktop](https://www.jetbrains.com/lp/compose-multiplatform/).

![Platform: macOS 12.0+](https://img.shields.io/badge/platform-macOS%2012.0+-blue)
![Build: Gradle](https://img.shields.io/badge/build-Gradle-green)
![Kotlin: 2.2.x](https://img.shields.io/badge/kotlin-2.2.x-purple)

> ⚠️ **Status: Beta — Code Complete, End-to-End Untested**
>
> All screens, components, and subsystems have been implemented. The app compiles successfully, 20+ extension JARs are pre-installed, and all data directories are set up. However, the full **Browse → Search → Select Episode → Play** flow has **not been tested end-to-end at runtime**. Expect bugs when the parts interact for the first time.
>
> See [CHANGELOG.md](CHANGELOG.md) for the development history and [the architecture plan](../architectural_rework_for_macos.md) for the full rework roadmap.

## Features

- **Browse Sources** — Discover anime from 20+ pre-installed extension sources
- **Library Management** — Organize your anime collection with categories
- **Video Player** — Full-featured mpv-based player with hardware acceleration (videotoolbox)
  - Playback speed control (0.25x–4.0x)
  - Audio track selection
  - Subtitle track selection with delay adjustment
  - Video equalizer (brightness, contrast, saturation, gamma)
  - Screenshot capture
  - Keyboard shortcuts (Space, ←→, ↑↓)
- **Tracker Sync** — MAL, AniList, Kitsu, and more via OAuth
- **Discord Rich Presence** — Show what you're watching on Discord
- **Backup & Restore** — Cross-compatible with Android `.tachibk` backups
- **Touch ID / PIN Lock** — Secure your library
- **20+ Color Schemes** — Including Monet, Nord, Material You, and more
- **macOS Native** — Native menu bar, Dock integration, Dark Mode support

## Architecture

The macOS port lives alongside the Android app in a single repository:

```
anikku/
├── app/                    # Android app (untouched)
├── domain/                 # Shared domain logic
├── data/                   # Shared data logic
├── source-api/             # Shared source API
├── core/                   # Shared core modules
├── presentation-core/      # Shared presentation utilities
│
└── macos/                  # macOS Compose Desktop project
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── src/main/kotlin/app/anikku/macos/
        ├── AnikkuApp.kt          # Entry point
        ├── AnikkuApplication.kt  # App lifecycle
        ├── di/                   # Koin DI modules
        ├── platform/             # macOS platform adapters
        ├── player/               # mpv integration (JNA)
        └── ui/                   # Compose Desktop UI
```

## Status

| Phase | Feature | Status |
|---|---|---|
| 0 | Build system & scaffolding | ✅ Complete |
| 1 | DI, Storage, Database, Logging | ✅ Complete |
| 2 | Domain & Data layer | ✅ Complete |
| 3 | Networking & Sources | ✅ Complete |
| 4 | UI Framework & Navigation | ✅ Complete |
| 5 | Screen-by-Screen UI | ✅ Code written |
| 6 | mpv Video Player (JNA) | ✅ Code written |
| 7 | Advanced Features | ✅ Code written |
| 8 | WebView Replacement | ✅ Complete |
| 9 | macOS Native Integration | ✅ Mostly complete |
| 10 | Packaging & Distribution | ❌ Not started |
| 11 | Testing & Polish | ⚠️ Tests exist, need end-to-end run |
| 12 | Documentation | ✅ This doc + BUILDING, INSTALL, guides |

### Detailed breakdown

✅ **Done and working:**
- Gradle build system with Compose Multiplatform Desktop
- Koin dependency injection (3 modules: Platform, Domain, App)
- JSON-backed preference store
- SQLDelight JDBC database driver
- Logback + kotlin-logging
- MacOSCookieJar (java.net.CookieManager backed)
- OkHttp network client with interceptors
- Extension JAR loading via URLClassLoader
- 20+ extension JARs pre-installed
- Material 3 theme with 20 color schemes
- Voyager navigation (tab navigator + per-tab inner navigators)
- All UI components (Scrollbar, FastScroller, SettingsItems, Toast, etc.)
- Settings screen with 15 sub-screens
- macOS menu bar (5 menus with keyboard shortcuts)
- Global keyboard shortcuts (⌘1-5, ⌘F, ⌘,, Space, arrows)
- Dock integration (badge count, dock menu)
- File picker, OAuth server, update checker
- Entitlements.plist for Hardened Runtime
- App icon (.icns)
- Sparkle appcast template
- Extension development guides (3 docs)
- Extension build pipeline (batch-build from source)

⚡ **Code written but untested end-to-end:**
- Library, Updates, History, Browse, Downloads, Stats screens
- AnimeDetailScreen (extension source calls)
- SourceBrowseScreen (search, browse)
- PlayerScreen with full controls UI
- PlayerViewModel (mpv initialization, playback, tracks, equalizer)
- MPVLib JNA bindings
- MPVEventLoop, MPVSoftwareRenderer, MPVVideoSurface
- MacOSHttpServer (NanoHTTPd for local video streaming)
- Tracker OAuth (TrackerManager, OAuthServer, TokenStore)
- Google Drive REST client
- Discord Rich Presence
- Biometric Auth (Touch ID + PIN)
- TorrentServerBridge
- Download manager
- Local HTTP server for video streaming

❌ **Not yet implemented:**
- jpackage DMG/.pkg packaging
- macOS code signing and notarization
- Sparkle auto-updater (template exists, needs wiring)

## Quick Start

```bash
# Clone the repository
git clone https://github.com/komikku-app/anikku.git
cd anikku

# Build and run (requires JDK 17)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew -p macos run
```

For detailed build instructions, see [BUILDING.md](BUILDING.md).
For installation instructions, see [INSTALL.md](INSTALL.md).

## Requirements

- **macOS 12.0+** (Monterey or later)
- **JDK 17+** (recommended: OpenJDK 17 via Homebrew or SDKMAN)
- **libmpv** (for hardware-accelerated video playback):
  ```bash
  brew install mpv
  ```

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Space` | Play / Pause |
| `←` / `→` | Seek backward / forward 10s |
| `↑` / `↓` | Volume up / down |
| `⌘,` | Open Settings |
| `⌘⇧F` | Toggle Fullscreen |
| `⌘1`–`⌘4` | Switch tabs |
| `⌘5` | Open More / Settings tab |
| `Esc` | Close player / Back |
| `⌘F` | Open search |
| `⌘W` | Close window |
| `⌘Q` | Quit app |

## File Locations

| Data | Location |
|---|---|
| App data | `~/Library/Application Support/Anikku/` |
| Preferences | `~/Library/Application Support/Anikku/preferences.json` |
| Database | `~/Library/Application Support/Anikku/data/anime.db` |
| Downloads | `~/Library/Application Support/Anikku/downloads/` |
| Backups | `~/Library/Application Support/Anikku/backups/` |
| Extensions | `~/Library/Application Support/Anikku/extensions/` |
| Logs | `~/Library/Logs/Anikku/` |
| Crash reports | `~/Library/Logs/Anikku/crash-*.log` |

## License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) / [GPL-compatible](https://github.com/komikku-app/anikku/blob/main/LICENSE)
