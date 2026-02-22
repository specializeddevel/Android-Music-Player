# AGENTS.md

This file guides agentic coding assistants working in this Android music player repository.

## Build Commands

```bash
# Build
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Build and install on connected device
./gradlew clean build            # Clean and full build

# Quality
./gradlew lint                   # Run lint checks (fatal errors fail build)
./gradlew lintDebug              # Run detailed lint with HTML report
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumentation tests (device required)

# Single test (full class or method)
./gradlew test --tests "ClassName"
./gradlew test --tests "ClassName.testMethodName"
./gradlew test --tests "com.raulburgosmurray.musicplayer.ui.PlaybackViewModelTest.testPlayPause"

# Debug APK location: app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

- **Single-module**: `app/` only
- **UI**: Jetpack Compose exclusively (no XML layouts)
- **Database**: Room with KSP (NOT kapt)
- **DI**: Manual via ViewModel factories (no Hilt/Dagger)
- **Package**: `com.raulburgosmurray.musicplayer`

```
com.raulburgosmurray.musicplayer/
├── data/           # Room entities, DAOs, repositories
├── ui/             # Compose screens, ViewModels
├── ui/theme/       # Material3 theme (Color, Type, Theme)
└── *.kt            # Top-level: Application, PlaybackService, etc.
```

## Tech Stack

- Kotlin 2.0.21, Java 11
- compileSdk 35, minSdk 24, targetSdk 35
- Dependencies: `gradle/libs.versions.toml`
- Media3 (ExoPlayer), CameraX, Kotlinx Serialization

## Code Style

### Imports
- Group: stdlib, AndroidX, project, third-party (sorted alphabetically)
- No wildcard imports (except `import androidx.*` for Room)
- Use `kotlinx.coroutines.flow.Flow` explicitly

### Naming
- Classes: PascalCase (`MainViewModel`)
- Functions: camelCase (`getAllBooks`)
- Constants: UPPER_SNAKE_CASE (`CHANNEL_ID`)
- Private properties: underscore prefix (`_books`)
- Composable functions: PascalCase (`MetadataEditorScreen`)

### Data Classes
- Use `data class` for entities and models
- Room: `@Entity(tableName = "table_name")`, `@PrimaryKey val id: String`
- Extension functions for conversions: `fun CachedBook.toMusic(): Music`
- JSON: `@Serializable` + `kotlinx.serialization.json.Json`

### DAOs
- `@Dao` interface, `Flow<List<T>>` for reactive queries
- `suspend` for writes, `@Insert(onConflict = OnConflictStrategy.REPLACE)` for upserts

### ViewModels
- Extend `AndroidViewModel(application: Application)`
- `viewModelScope` for coroutines, `StateFlow` for UI
- Initialize: `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`

### Compose Screens
- `@Composable` functions, `collectAsState()` for state
- Use Material3, adaptive layout: `LocalConfiguration.current.screenWidthDp` (tablet: 600dp)
- `@OptIn(ExperimentalMaterial3Api::class)` for experimental APIs
- Use `remember` and `rememberSaveable` for UI state
- Avoid passing ViewModels directly; use `LocalViewModelStoreOwner`

### Formatting
- 4-space indentation (no tabs), max 120 chars per line
- Opening brace same line: `fun foo() {`
- One blank line between top-level definitions
- Trailing commas for multi-line collections

### Coroutines
- `Dispatchers.IO` for database/file operations
- `withContext(Dispatchers.IO)` in suspend functions
- Prefer `Flow` over callbacks
- `viewModelScope.launch` for fire-and-forget

### Error Handling
- `try-catch` with specific exceptions
- Log with `Log.e(TAG, "message", exception)`
- Show user-friendly messages in UI
- Never catch broad `Exception` without re-throwing

### Testing
- Unit: `app/src/test/`, Instrumented: `app/src/androidTest/`
- Use MockK for mocking, Turbine for Flow testing
- Use `runTest` from `kotlinx-coroutines-test`

## Architecture

**Layers**: UI (Compose + ViewModel) → Service (PlaybackService) → Data (Room)

**Key ViewModels**:
- `MainViewModel`: Library scanning, sorting, favorites
- `PlaybackViewModel`: MediaController, seek state, bookmarks
- `SettingsViewModel`: User preferences
- `SyncViewModel`: Google Drive sync
- `LiteraTransferViewModel`: P2P file transfer

**Data Flow**:
1. Scan device → cache in Room → expose via `MainViewModel`
2. Play → `PlaybackViewModel` → `MediaController` → `PlaybackService` (ExoPlayer)
3. Progress saved every 5s during playback

## Important Notes

- Room database version 10, uses `fallbackToDestructiveMigration()`
- Feature flags: `BuildConfig.FEATURE_P2P_TRANSFER`, `BuildConfig.FEATURE_CLOUD_SYNC`
- Permissions: Android 13+ uses `READ_MEDIA_AUDIO`, older uses `READ_EXTERNAL_STORAGE`
- Audio focus enabled, auto-pause on headphone disconnect
- Smart rewind on resume (2-10s based on elapsed time)

## Before Committing

1. `./gradlew test` - all tests must pass
2. `./gradlew lint` - fix all warnings
3. `./gradlew assembleDebug` - must compile

## Lint

- Run `./gradlew lintDebug` for detailed HTML report
- Critical errors fail the build
- Suppress sparingly with `@Suppress("WarningName")`
