# AGENTS.md

Instructions for automated agents working in this repository.

## ⚠️ MANDATORY: Consult `ZEPP_OS_FINDINGS.md` before any Zepp OS work

**Before creating, modifying, or debugging ANY Zepp OS Mini Program code** (anything in the `zepp-sleep-detector/` directory), you **MUST** read and follow:

📄 **`ZEPP_OS_FINDINGS.md`** (in the project root)

This document contains **critical, battle-tested findings** from real testing on an Amazfit Active Max (Zepp OS 5.0, API 4.2). It includes:

- File extension requirements (`.page.js`, `.layout.js`)
- Correct import patterns (`import * as hmUI from "@zos/ui"`)
- Asset directory structure (`assets/<target>/icon.png`)
- Common pitfalls that cause black screens and crashes
- Verified working API surface for this device

**This document must be kept up-to-date.** When you discover new issues or solutions, add them to `ZEPP_OS_FINDINGS.md`. Verify against the official docs at https://docs.zepp.com/ and samples at https://github.com/zepp-health/zeppos-samples periodically.

## Build & Verify

```bash
./gradlew assembleDebug        # Must pass before committing
./gradlew test                  # Unit tests only (no device needed)
./gradlew lint                  # Android Lint — fix warnings before commit
```

Run order: `assembleDebug` → `lint` → `test`.

```bash
# Single test
./gradlew test --tests "com.raulburgosmurray.musicplayer.data.BookRepositoryTest"
./gradlew test --tests "*.BookRepositoryTest.testMethodName"
```

No ktlint, detekt, or ktfmt is configured — `./gradlew lint` is the only linter.

## Architecture Gotchas

- **Single module** — everything lives in `app/`. No multi-module boundaries.
- **Room DB version is 13**, not 9. Migrations 1→9 use destructive fallback; v10+ have explicit migrations. Schema files output to `app/schemas/`.
- **P2P transfer uses raw `java.net.ServerSocket`**, not Ktor (no Ktor dependency exists despite what CLAUDE.md says). Port 50001, simple TCP protocol.
- **`LiteraTransferViewModel` extends `AndroidViewModel`**, not `BaseViewModel` — it needs `Application` context for power/WiFi locks.
- **`Constants` and `FeatureFlags` are in the same file** (`FeatureFlags.kt`). Constants object contains audio timing, smart-rewind thresholds, file-filter rules, and P2P config.
- **`PREVIUS`** (typo of "previous") is an intent action string used by `PlaybackService` — fixing the typo is a breaking API change.
- **`viewBinding = true`** is enabled in build config but unused (Compose-only UI).
- **ProGuard rules are stock boilerplate** — `isMinifyEnabled = false` for release, so no custom keep rules are needed today.
- **Zepp OS Mini Program in `zepp-sleep-detector/`** — uses `*.page.js` and `*.layout.js` (NOT plain `.js`). Icons in `assets/<target>/icon.png`. Import `hmUI` with `import * as hmUI from "@zos/ui"`. Node **MUST** be v20 (not v22). For `zeus dev`, use target name with full name: `zeus dev -t "Amazfit Active Max"`. **There is a known bug with `zeus dev` causing an infinite rebuild loop** when assets change - use `zeus build` for one-shot compilation if needed. **App Service requires `requestPermission`** for `device:os.bg_service` before `appService.start()` — without it, the service starts but immediately stops. Use `url` (not `file`) in `appService.start()`. See **`ZEPP_OS_FINDINGS.md`** for full details.

## DI Pattern

Manual DI — no Hilt/Dagger/Koin. ViewModels are instantiated via `viewModelFactory` lambdas in Compose. `BaseViewModel` provides a `SharedFlow<UiEvent>` bus for one-shot UI events (snackbars, navigation).

## Testing Patterns

- **MockK** for mocking; **Turbine** for `Flow` testing; `runTest` from `kotlinx-coroutines-test`
- `PlaybackViewModel` tests use **reflection** (`getDeclaredField("controller")`) to inject a mocked `MediaController` into a private field
- `AppDatabase.Companion` is mocked via `mockkObject`
- `Dispatchers.setMain(testDispatcher)` / `resetMain()` for coroutine control in tests
- `SyncViewModelTest` mocks `GoogleSignIn.getLastSignedInAccount()` and `SharedPreferences`
- Unit tests at `app/src/test/`, instrumented tests at `app/src/androidTest/`
- There is no CI pipeline configured (no `.github/workflows/`)

## Dependency Management

All dependency versions are in `gradle/libs.versions.toml`. KSP version must match the Kotlin version exactly (`2.0.21-1.0.25`). The serialization plugin is applied at a hard-coded version (`2.0.21`) in `app/build.gradle.kts`, not from the version catalog.

## Key Unwritten Conventions

- Packages: `com.raulburgosmurray.musicplayer.data` for persistence, `.ui` for screens + ViewModels, root package for top-level utilities
- Extension functions for model conversions live alongside the entity (e.g., `CachedBook.toMusic()` in `CachedBook.kt`)
- `PlaybackService.saveCurrentProgressBlocking()` uses `runBlocking(Dispatchers.IO)` intentionally — called from `onTaskRemoved`/`onDestroy` to guarantee persistence before process death
- Equalizer preset is bridged between `PlaybackService` and `PlaybackViewModel` via `SharedPreferences("eq_prefs")`, not direct calls