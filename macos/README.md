# Anikku macOS

A native macOS anime watching application — a desktop port of the [Anikku](https://github.com/komikku-app/anikku) Android app, built with [Compose Multiplatform for Desktop](https://www.jetbrains.com/lp/compose-multiplatform/).

![Platform: macOS 12.0+](https://img.shields.io/badge/platform-macOS%2012.0+-blue)
![Build: Gradle](https://img.shields.io/badge/build-Gradle-green)
![Kotlin: 2.2.x](https://img.shields.io/badge/kotlin-2.2.x-purple)

## Features

- **Browse Sources** — Discover anime from dozens of extension sources
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

| Feature | Status |
|---|---|
| Build system & scaffolding | ✅ Complete |
| DI, Storage, Database, Logging | ✅ Complete |
| Domain & Data layer | ✅ Complete |
| Networking & Sources | ✅ Complete |
| UI Framework & Navigation | ✅ Complete |
| Screens (Library, Player, Settings) | ✅ Complete |
| mpv Video Player | ✅ Complete |
| OAuth / Tracker Sync | ✅ Complete |
| Discord Rich Presence | ✅ Complete |
| Biometric Auth (Touch ID) | ✅ Complete |
| Notifications | ✅ Complete |
| WebView Replacement | ✅ Complete |
| macOS Native (Menu, Dock, Shortcuts) | ✅ Complete |
| Packaging & Distribution | ✅ Complete |

## Quick Start

```bash
# Clone the repository
git clone https://github.com/komikku-app/anikku.git
cd anikku

# Build and run
cd macos
./gradlew run
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
| `Esc` | Close player / Back |

## License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) / [GPL-compatible](https://github.com/komikku-app/anikku/blob/main/LICENSE)
