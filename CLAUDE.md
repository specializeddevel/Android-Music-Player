# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

- Before each new feature you should compile the app to find compilation errors.
```bash
# Build
./gradlew assembleDebug          # Build debug APK  (app/build/outputs/apk/debug/app-debug.apk)
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Build and install on connected device
./gradlew clean build            # Clean and full build

# Quality
./gradlew lint                   # Run lint checks (fatal errors fail build)
./gradlew lintDebug              # Run detailed lint with HTML report
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumentation tests (device required)

# Single test
./gradlew test --tests "ClassName"
./gradlew test --tests "ClassName.testMethodName"
```

- compileSdk 35, minSdk 24, targetSdk 35, Java 11
- KSP is used for Room annotation processing (not kapt)
- All dependencies are managed via version catalog at `gradle/libs.versions.toml`

## Before Committing

1. `./gradlew test` — all tests must pass
2. `./gradlew lint` — fix all warnings; suppress sparingly with `@Suppress("WarningName")`
3. `./gradlew assembleDebug` — must compile cleanly

## Architecture

**Single-module Android app** (`app/`) using Jetpack Compose for all UI with no XML layouts.

### Layers

- **UI layer** (`ui/`): Compose screens + ViewModels with `StateFlow`. Manual DI via ViewModel factories — no Hilt.
- **Service layer**: `PlaybackService` extends `MediaSessionService` (Media3). Runs as a foreground service; communication with the UI layer goes through `MediaController` in `PlaybackViewModel`.
- **Data layer** (`data/`): Room database (`AppDatabase`, version 9, destructive migration) with 5 entities: `AudiobookProgress`, `CachedBook`, `FavoriteBook`, `Bookmark`, `QueueItem`. DAOs expose `Flow` for reactive queries. Repositories are lightweight wrappers over DAOs. `GoogleDriveService` and `MusicScanner`/`MetadataHelper` also live here.

### Root-package utilities

`ApplicationClass`, `PermissionManager`, `ShakeDetector`, `BookIdEncoder`, `DialogHelper`, `FeatureFlags` (constants), `Constants`.

### Key ViewModels

| ViewModel | Responsibility |
|---|---|
| `MainViewModel` | Library scanning, sorting, search, favorites |
| `PlaybackViewModel` | MediaController binding, seek state, sleep timer, bookmarks |
| `SettingsViewModel` | User preferences (theme, shake detection, layout) |
| `SyncViewModel` | Google Drive bi-directional sync |
| `LiteraTransferViewModel` | P2P file transfer via Ktor HTTP server/client (port 50001) |
| `MetadataEditorViewModel` | Edit and persist per-book metadata |

All ViewModels extend `BaseViewModel` and use `viewModelScope` for coroutines + `StateFlow` for UI state. Initialize flows with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`.

### Navigation

`MainActivity` hosts a `NavHost` with routes: `onboarding`, `main`, `favorites`, `settings`, `player/{from}`, `transfer?bookId={bookId}`, `metadata_editor?bookId={bookId}`. Navigation is driven from `MainActivity` callbacks passed down to composables.

Start destination is `onboarding` when no library root URIs are configured, otherwise `main`.

### Data Flow

1. **Book loading**: Scan device via SAF (up to 3 folders) or full MediaStore scan → cache in Room (`CachedBook`) → exposed via `MainViewModel.books`
2. **Playback**: `MainViewModel` triggers play → `PlaybackViewModel` sends command via `MediaController` → `PlaybackService` (ExoPlayer/Media3) plays audio
3. **Progress persistence**: `PlaybackService` saves position to Room every 10 seconds during playback; restored on resume
4. **P2P transfer**: `LiteraTransferViewModel` starts a Ktor server; peer discovers via QR code (ZXing generation + ML Kit scanning via CameraX) or manual IP entry

### Feature Flags

Two build-time feature flags in `BuildConfig` (declared in `FeatureFlags.kt`):
- `FEATURE_P2P_TRANSFER` — enables `LiteraTransferViewModel` / transfer screen
- `FEATURE_CLOUD_SYNC` — enables `SyncViewModel` / Google Drive sync

### Permissions

`PermissionManager` handles version-aware storage permissions:
- Android 13+: `READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS`
- Android 5–12: `READ_EXTERNAL_STORAGE`

### Audio Engine

Media3 ExoPlayer with `AUDIO_CONTENT_TYPE_SPEECH`, audio focus enabled, auto-pause on headphone disconnect, smart rewind (2–20 s) on resume based on elapsed time since pause. Skip-back button = 30 s, skip-forward button = 10 s.

### Adaptive Layout

Tablet breakpoint at 600 dp window width. `LocalConfiguration.current.screenWidthDp` drives layout switching between list (phone) and grid (tablet) throughout `MainScreen` and `PlayerScreen`.

## Code Style

### Naming

- Classes/Composables: `PascalCase` — `MainViewModel`, `MetadataEditorScreen`
- Functions: `camelCase` — `getAllBooks`
- Constants: `UPPER_SNAKE_CASE` — `CHANNEL_ID`
- Private backing properties: underscore prefix — `_books`

### Key Patterns

- **Entities**: `@Entity(tableName = "table_name")`, `@PrimaryKey val id: String`; use `data class`
- **DAO**: `@Dao` interface, `Flow<List<T>>` for reactive queries, `suspend` for writes, `@Insert(onConflict = OnConflictStrategy.REPLACE)` for upserts
- **JSON**: `@Serializable` + `kotlinx.serialization.json.Json`; extension functions for conversions (`fun CachedBook.toMusic(): Music`)
- **Coroutines**: `Dispatchers.IO` for DB/file ops; `viewModelScope.launch` for fire-and-forget; prefer `Flow` over callbacks
- **Compose**: `collectAsState()` for state, `@OptIn(ExperimentalMaterial3Api::class)` for experimental APIs, `remember`/`rememberSaveable` for UI state

### Formatting

- 4-space indentation, max 120 chars per line
- Opening brace same line: `fun foo() {`
- One blank line between top-level definitions; trailing commas in multi-line collections
- No wildcard imports (except `import androidx.*` for Room)

### Testing

- Unit tests: `app/src/test/`, instrumented: `app/src/androidTest/`
- Use **MockK** for mocking, **Turbine** for Flow testing, `runTest` from `kotlinx-coroutines-test`
