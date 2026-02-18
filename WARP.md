# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

This is an advanced Android Music Player application built with Kotlin that provides comprehensive audio playbook functionality. The app is designed as an audiobook/music player with advanced features like variable speed playback, sleep timers, favorites management, and persistent playback state restoration.

## Development Commands

### Building and Running

```powershell
# Clean and build the project
./gradlew clean build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug build to connected device
./gradlew installDebug

# Run on connected device/emulator
./gradlew run
```

### Testing

```powershell
# Run unit tests
./gradlew test

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "com.raulburgosmurray.musicplayer.ExampleUnitTest"

# Run tests with reports
./gradlew test jacocoTestReport
```

### Code Quality

```powershell
# Lint check
./gradlew lint

# Generate lint report
./gradlew lintDebug

# Check for dependency updates
./gradlew dependencyUpdates
```

## Architecture Overview

### Core Components Architecture

**Service-Based Playback Architecture:**
- `MusicService`: Foreground service that handles audio playback, notifications, and media session management
- `PlayerActivity`: Main playback interface with comprehensive controls (speed, sleep timer, skip controls)
- `MainActivity`: Home screen with music library, search, and navigation to other features
- `NotificationReceiver`: Handles media control actions from notification panel

**Data Layer:**
- `Music`: Core data class representing audio files with metadata (title, artist, album, duration, path, artwork)
- `PlaybackState`: Manages persistent playback position across app sessions
- Audio metadata is extracted using JAudioTagger library for enhanced file information

**UI Architecture:**
- Uses ViewBinding for all layouts
- RecyclerView adapters: `MusicAdapter` (main library), `FavoriteAdapter` (favorites grid)
- Fragment-based mini-player: `NowPlaying` fragment provides persistent playback controls
- Material Design 3 components throughout

### Key Features Implementation

**Playback State Management:**
- Automatic saving/restoration of playback position per audio file
- Continues playing last audio on app restart
- JSON-based state persistence in app's internal storage

**Advanced Audio Controls:**
- Variable playback speed (0.5x - 2x) with Android's PlaybackParams
- Skip controls: 10s/60s forward/backward with long-press for 30s/5min
- Sleep timer with multiple preset options (5-120 minutes)
- Audio focus management for proper integration with system audio

**Permission Handling:**
- Version-aware permission requests (Android 5-13+)
- `PermissionManager` handles different permission requirements per API level
- Graceful degradation when permissions are denied

## Project Structure

```
app/src/main/java/com/raulburgosmurray/musicplayer/
├── Activities/
│   ├── MainActivity.kt              # Main music library interface
│   ├── PlayerActivity.kt           # Full-screen player with controls
│   ├── FavoritesActivity.kt        # Favorites management
│   └── PlaylistActivity.kt         # Playlist management
├── Services/
│   ├── MusicService.kt             # Background playback service
│   └── NotificationReceiver.kt     # Media controls from notification
├── Data/
│   ├── Music.kt                    # Core data model & utilities
│   └── PlaybackState.kt           # Playback persistence model
├── Adapters/
│   ├── MusicAdapter.kt             # Main library RecyclerView
│   └── FavoriteAdapter.kt         # Favorites grid
├── Fragments/
│   └── NowPlaying.kt              # Mini-player fragment
└── Utils/
    ├── ApplicationClass.kt         # App initialization & notification channels
    ├── PermissionManager.kt        # Version-aware permission handling
    └── ColorUtilsImproved.kt      # UI theming utilities
```

## Development Guidelines

### Adding New Features

**Audio Features:**
- All playback logic should go through `MusicService` to maintain proper foreground service behavior
- Use `PlayerActivity.Companion` objects for shared playback state
- Always save playback state using `Music.savePlaybackState()` before position changes

**UI Components:**
- Use ViewBinding for all new layouts
- Follow Material Design 3 patterns established in existing activities
- RecyclerView implementations should extend the established adapter pattern

**Permissions:**
- Audio file access requires different permissions based on Android version
- Use `PermissionManager.checkAndRequestPermissions()` for consistent permission handling
- Test on Android 5, 10, and 13+ for permission compatibility

### Debugging Audio Issues

**Common Playback Problems:**
- Check `MusicService.mediaPlayer` state in debugger
- Verify `PlayerActivity.isPlaying` matches actual MediaPlayer state
- Ensure audio focus is properly managed in `MusicService`

**State Persistence Issues:**
- Playback states are stored as JSON files in `context.filesDir/playback_states/`
- Each audio file has its own state file named by audio ID
- Check file permissions if states aren't persisting

### Key Dependencies

- **JAudioTagger** (3.0.1): Metadata extraction from audio files
- **Glide** (5.0.0-rc01): Image loading for album artwork
- **TedPermission** (3.4.2): Simplified permission request handling
- **Gson** (2.9.0): JSON serialization for playback state persistence
- **Android Media** (1.7.0): Enhanced media session support

## Testing Approach

**Unit Tests:** Focus on utility functions in `Music` companion object and data transformations
**Integration Tests:** Test audio playback scenarios, permission flows, and state persistence
**UI Tests:** Verify RecyclerView interactions, player controls, and navigation flows

The app targets minSdk 24 (Android 7.0) and uses modern Android development practices with Kotlin coroutines for background operations and proper lifecycle management throughout.
