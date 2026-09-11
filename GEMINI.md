# Gemini Project Context: Litera Audiobook Player

This document provides an overview of the "Litera" Audiobook Player project to guide AI-assisted development.

## 1. Project Overview

Litera is a modern audiobook player for Android. It is built using Kotlin and follows modern Android development practices. The project also contains a companion app for Zepp OS, likely for sleep detection features.

### 1.1. Main Android App (`:app` module)

*   **Purpose:** An audiobook player with library management, playback, and organizational features.
*   **Core Technologies:**
    *   **Language:** Kotlin
    *   **UI:** Jetpack Compose with Material3
    *   **Architecture:** MVVM + Clean Architecture
    *   **Database:** Room
    *   **Media Playback:** Media3 (ExoPlayer)
    *   **Dependency Injection:** Manual (via ViewModel factories)
*   **Key Features:**
    *   Local audio file scanning and library creation.
    *   Advanced playback controls (speed, sleep timer, smart rewind).
    *   Bookmarking and progress tracking.
    *   **Conditional Features (via BuildConfig flags):**
        *   `FEATURE_P2P_TRANSFER`: P2P file transfer using local WiFi/QR codes.
        *   `FEATURE_CLOUD_SYNC`: Bi-directional synchronization with Google Drive.
        *   `FEATURE_SLEEP_DETECTION`: Integration with an external device (see Zepp OS app) to automatically manage playback based on sleep status.

### 1.2. Companion Zepp OS App (`/zepp-sleep-detector`)

*   **Purpose:** A small companion app for Zepp OS (smartwatches like Amazfit).
*   **Technology:** JavaScript using the Zepp OS SDK.
*   **Role:** This app is not part of the main Android Gradle build. It communicates with the main Android app, likely over Bluetooth, to provide sleep detection data. This enables the `FEATURE_SLEEP_DETECTION` in the Android app.

## 2. Development Workflow

### 2.1. Building and Running the Android App

Standard Gradle commands are used. The project includes a Gradle wrapper (`gradlew`).

*   **Build a debug APK:**
    ```bash
    ./gradlew assembleDebug
    ```
*   **Install on a connected device/emulator:**
    ```bash
    ./gradlew installDebug
    ```
*   **Run unit tests:**
    ```bash
    ./gradlew test
    ```
*   **Run lint checks:**
    ```bash
    ./gradlew lint
    ```

### 2.2. Modifying Feature Flags

Feature flags are defined in `app/build.gradle.kts` within the `buildTypes` block. To enable or disable a feature for a build, modify the boolean value of the corresponding `buildConfigField`.

Example for `debug` build:
```kotlin
debug {
    buildConfigField("boolean", "FEATURE_CLOUD_SYNC", "true") // "true" or "false"
}
```

## 3. Architectural Notes

*   **Code Structure:** The main application source is located in `app/src/main/java/com/raulburgosmurray/musicplayer/`. The `README.md` provides a good breakdown of the package structure (`data`, `ui`, etc.).
*   **Permissions:** The `AndroidManifest.xml` declares permissions required for file access (`READ_MEDIA_AUDIO`), background playback (`FOREGROUND_SERVICE`), networking (`INTERNET`), and device hardware (`BLUETOOTH`, `CAMERA`).
*   **Build System:** The project uses Gradle's version catalog (`libs` in `build.gradle.kts`) for dependency management, though it's aliased.
*   **Versioning:** The app version is managed manually inside `app/build.gradle.kts`.
*   **Schema:** Room database schemas are exported to `app/schemas/`.

## 4. AI Agent Guidelines and Gotchas

This section contains critical, real-world instructions and corrections sourced from project-specific agent documentation (`AGENTS.md`, `CLAUDE.md`).

### ⚠️ IMPORTANT: Zepp OS Development

**Before creating, modifying, or debugging ANY code in the `zepp-sleep-detector/` directory**, you **MUST** read, understand, and follow the instructions in:

📄 **`ZEPP_OS_FINDINGS.md`** (in the project root)

This document contains non-obvious, device-tested requirements for file extensions, import patterns, asset structures, and common pitfalls. It is the source of truth for Zepp OS development.

### 4.1. Key Architectural Gotchas & Corrections

This is a list of important implementation details and corrections to potential misconceptions.

*   **Database:** The Room DB version is **13**, not 9. Migrations from version 10 onwards are explicit and non-destructive.
*   **P2P Transfer:** This feature uses a raw **`java.net.ServerSocket`** on port 50001. It does **NOT** use Ktor.
*   **DI Pattern:** Dependency Injection is done **manually**. ViewModels are created in Compose using `viewModelFactory` lambdas. There is no Hilt, Dagger, or Koin.
*   **ViewModel Structure:** Most ViewModels extend `BaseViewModel`. However, `LiteraTransferViewModel` extends `AndroidViewModel` to get the `Application` context for managing power/WiFi locks.
*   **Constants:** The `Constants` and `FeatureFlags` objects are located in the same file, `FeatureFlags.kt`.
*   **Intent Action Typo:** A `PlaybackService` intent action for "previous" is misspelled as **`PREVIUS`**. Do not "fix" this, as it would be a breaking API change within the app.
*   **Testing:** Tests use **MockK** and **Turbine**. Note that `PlaybackViewModel` tests use reflection to inject a mock `MediaController`.
*   **Build Config:** `viewBinding = true` is enabled but the feature is unused, as the UI is 100% Jetpack Compose. `isMinifyEnabled = false` for release builds, so ProGuard is not currently a factor.

### 4.2. General Code Style and Patterns

*   **Naming Conventions:**
    *   Classes/Composables: `PascalCase`
    *   Functions: `camelCase`
    *   Constants: `UPPER_SNAKE_CASE`
    *   Private backing properties for `StateFlow`: `_name`
*   **Coroutines:**
    *   Use `Dispatchers.IO` for database and file system operations.
    *   Use `viewModelScope.launch` for fire-and-forget UI-related tasks.
    *   Prefer `Flow` for reactive data streams from the data layer.
*   **JSON Serialization:** The project uses `kotlinx.serialization.json`. Look for `@Serializable` annotations on data classes.
*   **DAO Pattern:** DAOs should expose `Flow<List<T>>` for queries and use `suspend` functions for write operations (`@Insert`, `@Update`, `@Delete`). Use `@Insert(onConflict = OnConflictStrategy.REPLACE)` for upsert logic.
