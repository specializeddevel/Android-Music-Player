# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

- Before each new feature you shoud compile the app to find compilation errors. 
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Build and install on connected device
./gradlew clean build            # Clean and full build
./gradlew lint                   # Run lint checks
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumentation tests (device required)
```

- compileSdk 35, minSdk 24, targetSdk 35, Java 11
- KSP is used for Room annotation processing (not kapt)
- All dependencies are managed via version catalog at `gradle/libs.versions.toml`

## Architecture

**Single-module Android app** (`app/`) using Jetpack Compose for all UI with no XML layouts.

### Layers

- **UI layer** (`ui/`): Compose screens + ViewModels with `StateFlow`. Manual DI via ViewModel factories — no Hilt.
- **Service layer**: `PlaybackService` extends `MediaSessionService` (Media3). Runs as a foreground service; communication with the UI layer goes through `MediaController` in `PlaybackViewModel`.
- **Data layer** (`data/`): Room database (`AppDatabase`, version 9, destructive migration) with 5 entities: `AudiobookProgress`, `CachedBook`, `FavoriteBook`, `Bookmark`, `QueueItem`. DAOs expose `Flow` for reactive queries. `GoogleDriveService` handles cloud sync.

### Key ViewModels

| ViewModel | Responsibility |
|---|---|
| `MainViewModel` | Library scanning, sorting, search, favorites |
| `PlaybackViewModel` | MediaController binding, seek state, sleep timer, bookmarks |
| `SettingsViewModel` | User preferences (theme, shake detection, layout) |
| `SyncViewModel` | Google Drive bi-directional sync |
| `LiteraTransferViewModel` | P2P file transfer via Ktor HTTP server/client (port 50001) |

### Navigation

`MainActivity` hosts a `NavHost` with routes: `onboarding`, `main`, `favorites`, `settings`, `player/{from}`, `transfer?bookId={bookId}`. Navigation is driven from `MainActivity` callbacks passed down to composables.

### Data Flow

1. **Book loading**: Scan device via SAF → cache in Room (`CachedBook`) → exposed via `MainViewModel.books`
2. **Playback**: `MainViewModel` triggers play → `PlaybackViewModel` sends command via `MediaController` → `PlaybackService` (ExoPlayer/Media3) plays audio
3. **Progress persistence**: `PlaybackService` saves position to Room every 5 seconds during playback; restored on resume
4. **P2P transfer**: `LiteraTransferViewModel` starts a Ktor server; peer discovers via QR code (ZXing generation + ML Kit scanning via CameraX) or manual IP entry

### Permissions

`PermissionManager` handles version-aware storage permissions:
- Android 13+: `READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS`
- Android 5–12: `READ_EXTERNAL_STORAGE`

### Audio Engine

Media3 ExoPlayer with `AUDIO_CONTENT_TYPE_SPEECH`, audio focus enabled, auto-pause on headphone disconnect, smart rewind (2–10 s) on resume based on elapsed time since pause.

### Adaptive Layout

Tablet breakpoint at 600 dp window width. `LocalConfiguration.current.screenWidthDp` drives layout switching between list (phone) and grid (tablet) throughout `MainScreen` and `PlayerScreen`.
