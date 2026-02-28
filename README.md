# Litera - Audiobook Player

A modern Android audiobook player built with Jetpack Compose, featuring library management, playback controls, cloud sync, and P2P file transfer.

## Features

### Library Management
- Scan device storage or select specific folders via SAF
- Automatic filtering of voice messages and non-audiobook files
- Support for MP3, M4A, M4B, AAC, WAV, OGG, FLAC formats
- Search and filter books by title/artist

### Playback
- Background playback with Media3 ExoPlayer
- Sleep timer with warning
- Playback speed control
- Smart rewind on resume (2-20 seconds based on pause duration)
- Skip forward (10s) / backward (30s)
- Progress saved every 10 seconds
- Queue management

### Organization
- Mark books as read/unread
- Filter by: All, Completed, In Progress
- Sort by: Title, Artist, Progress, Recently played
- Favorites list
- Bookmarks

### Cloud & Transfer (Optional Features)
- Google Drive bi-directional sync
- P2P file transfer via local WiFi network

## Tech Stack

- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose with Material3
- **Database**: Room 9 with KSP
- **DI**: Manual (ViewModel factories)
- **Media**: Media3 ExoPlayer
- **Architecture**: MVVM + Clean Architecture

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run lint checks
./gradlew lint

# Run unit tests
./gradlew test
```

## Project Structure

```
app/src/main/java/com/raulburgosmurray/musicplayer/
├── data/           # Room entities, DAOs, repositories
├── ui/             # Compose screens, ViewModels
├── ui/theme/       # Material3 theme
├── PlaybackService.kt
├── ApplicationClass.kt
├── FeatureFlags.kt
└── Constants.kt
```

## Key ViewModels

| ViewModel | Responsibility |
|----------|----------------|
| MainViewModel | Library scanning, sorting, search, favorites |
| PlaybackViewModel | Playback controls, seek, sleep timer, bookmarks |
| SettingsViewModel | User preferences |
| SyncViewModel | Google Drive sync |
| LiteraTransferViewModel | P2P file transfer |
| MetadataEditorViewModel | Edit book metadata |

## Permissions

- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` - Access audio files
- `INTERNET` - Cloud sync
- `ACCESS_WIFI_STATE` - P2P transfer
- `FOREGROUND_SERVICE` - Background playback
- `POST_NOTIFICATIONS` - Playback notifications
- `VIBRATE` - Feedback
- `WAKE_LOCK` - Prevent sleep during playback

## Version

Current: **1.0.220226001**

## License

Private - All rights reserved
