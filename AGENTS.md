# Repository Guidelines

## Project Overview
Android app (`it.federicorapetti.recalls`) that aggregates product-recall alerts from two sources — the **EU Safety Gate** system (non-food products) and the Italian **Ministero della Salute** (operator food recalls + Ministry food-safety warnings) — into a single Room-backed list, synced in the background via WorkManager, with per-source notifications. UI is Jetpack Compose, Material 3 **Expressive** (`material3 1.5.0-alpha29`), Navigation 3. No backend of our own; the app talks directly to `ec.europa.eu` and `www.salute.gov.it`.

`docs/DATA_SOURCES.md` contains the verified request/response shapes for both APIs and is worth reading before touching the remote clients or repository sync logic.

## Architecture & Data Flow
Manual DI, no Hilt. Single container `AppContainer.kt` is built once in `RecallsApp.onCreate()` and reached everywhere via `(application as RecallsApp).container`.

```
SafetyGateApi / SaluteApi  →  SafetyGateMapper / SaluteMapper  →  RecallContent
        ↓ (OkHttp, Chrome UA)          ↓ (DTO → domain)              ↓
                                                          RecallRepository.sync()
                                                                      ↓ (Mutex-guarded)
                                                     Room: RecallDao.upsertContent()
                                                                      ↓
                                              RecallDao.observeAll(): Flow<List<RecallEntity>>
                                                                      ↓
                            RecallListViewModel / RecallDetailViewModel / SettingsViewModel
                                                                      ↓
                                        Compose UI (Navigation 3: List / Detail / Settings)
```

Background path: `RecallSyncWorker` (WorkManager, periodic + one-shot) calls the same `repository.sync()`, then `RecallNotifier` posts per-source notifications for genuinely new items. Notification taps carry intent extras that `MainActivity` turns into a `LaunchRequest`, consumed once by `RecallsNavHost` to reset the back stack to the list and optionally push a detail screen or apply a source filter.

`RecallSource` enum (`data/model/RecallSource.kt`): `SAFETY_GATE`, `IT_OPERATOR`, `IT_MINISTRY`. Drives Room `id` prefixes (`SG:`, `IT:`, `ITW:`), the notification channel/toggle pairing (EU vs Italy — `IT_OPERATOR` and `IT_MINISTRY` share the "Italy" channel via `RecallSource.isItaly`), and UI filtering.

## Key Directories
All under `app/src/main/java/it/federicorapetti/recalls/`:

- `data/remote/safetygate/` — Safety Gate DTOs (`SafetyGateDtos.kt`), API client (`SafetyGateApi.kt`), DTO→domain mapper (`SafetyGateMapper.kt`).
- `data/remote/salute/` — Salute DTOs (`SaluteDtos.kt`), hand-rolled RSS parser (`MinistryRssParser.kt`), API client (`SaluteApi.kt`), mapper (`SaluteMapper.kt`).
- `data/remote/Http.kt` — shared `OkHttpClient` factory, the Chrome User-Agent interceptor, the `execute()` coroutine helper, and the shared `AppJson` (`kotlinx.serialization.json.Json`) instance. Everything HTTP-related goes through here.
- `data/local/` — Room: `RecallEntity.kt` (entity + `RecallContent` partial entity + `SourceStateEntity`), `RecallDao.kt`, `RecallDatabase.kt`.
- `data/settings/SettingsRepository.kt` — DataStore Preferences wrapper (sync interval, per-source notification toggles, permission-asked flag).
- `data/RecallRepository.kt` — the only place that orchestrates a sync across all three sources; owns the 90-day history window and baseline/new-item logic.
- `sync/` — `SyncScheduler.kt` (WorkManager enqueue), `RecallSyncWorker.kt` (`CoroutineWorker`), `RecallNotifier.kt` (channels + notifications).
- `ui/list/`, `ui/detail/`, `ui/settings/` — screen + ViewModel per feature (`RecallListScreen`/`RecallListViewModel`, etc.), plus `RecallCard.kt`, `SourceFilter.kt`.
- `ui/navigation/` — `NavKeys.kt` (Navigation 3 serializable keys), `LaunchRequest.kt`, `RecallsNavHost.kt`.
- `ui/theme/Theme.kt` — `MaterialExpressiveTheme` + `MotionScheme.expressive()` setup.
- `ui/common/RiskLabels.kt` — Safety Gate risk-enum → localized string mapping + generic enum humanizer.

## Development Commands
Run from the repo root with JDK on PATH (developed/verified against a JDK 21 daemon; `gradle/gradle-daemon-jvm.properties` pins toolchain `25`).

```
gradlew.bat :app:testDebugUnitTest          # unit tests (JVM only)
gradlew.bat :app:assembleDebug              # debug APK -> app/build/outputs/apk/debug/app-debug.apk
gradlew.bat clean :app:assembleDebug        # clean rebuild (use after dependency/version changes)
gradlew.bat :app:testDebugUnitTest :app:assembleDebug   # both in one invocation
```

No lint/format tooling is configured (no `.editorconfig`, no ktlint/detekt). `kotlin.code.style=official` is set in `gradle.properties`; match existing formatting by hand.

Manual device/emulator smoke test: install the debug APK, launch, grant the notification permission, verify EU + Italy cards load with thumbnails, exercise the source filter toolbar and a detail screen. See `docs/MANUAL_TESTING.md` for the full scripted checklist (including sqlite3-based new-item/notification testing).

## Code Conventions & Common Patterns

**DTO → domain mapping.** Every remote source has `{Source}Dtos.kt` (all fields nullable or defaulted, lists default `emptyList()` — forward-compatible against upstream schema drift) and a separate `{Source}Mapper.kt` with an extension function `fun SourceDto.toContent(...): RecallContent`. Never put mapping logic in the API client or the repository.

**Room partial upsert preserves read state.** `RecallContent` is `RecallEntity` minus `isNew`/`isRead`. `RecallDao.upsertContent(items: List<RecallContent>)` is `@Upsert(entity = RecallEntity::class)`, so a re-synced row never clobbers a user's read/unread state. When adding columns to `RecallEntity`, add them to `RecallContent` too (unless they're flag-like state that sync must not own).

**Sync orchestration lives in one place: `RecallRepository.sync()`.** Mutex-guarded (`Mutex.withLock`), runs the three sources sequentially, each in its own `try/catch` that rethrows `CancellationException` and otherwise records the error into `SyncResult.errors` — one source failing must never abort the others. `RecallSyncWorker` returns `Result.retry()` only when *all three* errors are `IOException` (network-flavored); otherwise it's `Result.success()` even with partial failures, and failures surface as a snackbar/error state in the list UI.

**90-day history window** is computed fresh at the start of every `sync()` call (`now - 90 days`), not cached. Items older than it are simply never inserted; existing rows already in Room are never pruned by the client.

**Baseline sync is silent.** `SourceStateEntity.baselineDone` gates whether newly-seen ids get `isNew = 1` and get surfaced in `SyncResult.newItems`. First-ever sync per source populates Room without marking anything new or triggering a notification. If you touch `RecallRepository.store()`, preserve this invariant — it's why there's no notification spam on first install.

**HTTP: always go through `Http.kt`'s `execute()`.** Real bug fixed during development: `execute()` must wrap the *entire* response-body read in `withContext(Dispatchers.IO)`, not just the `newCall().execute()` connection step:

```kotlin
suspend fun <T> OkHttpClient.execute(request: Request, block: (Response) -> T): T =
    withContext(Dispatchers.IO) {
        newCall(request).execute().use(block)
    }
```

Callers pass a `block` that does the streaming JSON decode (`AppJson.decodeFromStream(...)`) *inside* this call. If a caller instead does `client.execute(request).use { AppJson.decodeFromStream(...) }` with the old signature, the decode runs on whatever dispatcher the caller is on — this crashed with `NetworkOnMainThreadException` when `RecallListViewModel`'s init-time refresh (running on `Dispatchers.Main.immediate` via `viewModelScope`) streamed the multi-page Safety Gate response. Any new remote call must follow the `client.execute(request) { response -> ... }` pattern used in `SafetyGateApi.kt` / `SaluteApi.kt`.

**Chrome User-Agent is mandatory for `salute.gov.it`.** `buildHttpClient()` in `Http.kt` installs an interceptor that stamps every request with `BROWSER_USER_AGENT` (a real desktop Chrome UA) — the site's Gcore bot shield 403s anything else, including image fetches. Coil is wired to the same `OkHttpClient` via `RecallsApp`'s `SingletonImageLoader.Factory` (`OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })`), so images inherit it too. Never build a second `OkHttpClient`/`ImageLoader` that bypasses this.

**Safety Gate search body is strict.** `SafetyGateApi.search()` POSTs a specific full JSON shape (see `docs/DATA_SOURCES.md`) — a trimmed-down body returns HTTP 405. If you touch this call, keep every field, especially `criteria.year`.

**Serialization.** One shared `AppJson` (`data/remote/Http.kt`): `ignoreUnknownKeys = true`, `explicitNulls = false`, `coerceInputValues = true`. Use `AppJson.decodeFromStream(...)` for the large (13.8 MB) Salute operator payload — never load it into a `String` first.

**Dependency injection.** Manual, via `AppContainer.kt` — a flat list of lazily-nothing, eagerly-built singletons (`httpClient`, `database`, `dao`, `settings`, `safetyGateApi`, `saluteApi`, `repository`, `notifier`, `scheduler`, `appScope`). No Hilt/Koin; keep it this way unless the singleton count grows substantially. ViewModels are constructed with a plain `viewModel { XViewModel(container.xxx) }` factory lambda inside each Navigation 3 `entry<Key> { }` block in `RecallsNavHost.kt` — not `ViewModelProvider.Factory` classes.

**State management.** ViewModels expose a single `StateFlow<UiState>` built with `combine()` over repository/settings flows (see `RecallListViewModel` combining `repository.observeAll()`, filter, query, unread-only, and sync status). Navigation-triggered actions (notification taps) flow through `MainActivity`'s `MutableStateFlow<LaunchRequest?>`, consumed once by `RecallsNavHost` then cleared — don't reach into ViewModels directly from `MainActivity`.

**Async.** Kotlin coroutines + `Flow` throughout; no RxJava/LiveData. `RecallRepository` uses `Mutex` to serialize concurrent `sync()` calls (pull-to-refresh vs. worker vs. "Check now" button racing). Long-lived app-scope work uses `AppContainer.appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.

## Important Files
- `data/remote/Http.kt` — shared HTTP client, UA interceptor, `execute()` helper, `AppJson`. Read before adding any network call.
- `data/RecallRepository.kt` — sync orchestration, 90-day window, baseline/new-item semantics, per-source error isolation.
- `data/local/RecallEntity.kt` — `RecallEntity`, `RecallContent` (partial upsert type), `SourceStateEntity`.
- `data/remote/safetygate/SafetyGateApi.kt` — exact POST body Safety Gate requires (405 if trimmed).
- `ui/theme/Theme.kt` — Material 3 Expressive theme setup (`MaterialExpressiveTheme`, `MotionScheme.expressive()`).
- `AppContainer.kt` / `RecallsApp.kt` — DI wiring and app entry point (channel creation, initial periodic-sync scheduling, Coil `ImageLoader` factory).
- `MainActivity.kt` — intent → `LaunchRequest` translation for notification deep links.
- `docs/DATA_SOURCES.md` — verified API request/response shapes for both sources; consult before changing remote clients. `docs/MANUAL_TESTING.md` — scripted device/emulator checklist.
- `gradle/libs.versions.toml` + root `build.gradle.kts` — version catalog and the KGP classpath override (see below).

## Runtime/Tooling Preferences
- **Build system:** Gradle via the wrapper (`gradlew.bat` on Windows) — always use the wrapper, never a global `gradle`.
- **Gradle:** 9.6.0 (`gradle/wrapper/gradle-wrapper.properties`). Daemon JVM toolchain: 25 (`gradle/gradle-daemon-jvm.properties`); app bytecode target is Java 11 (`sourceCompatibility`/`targetCompatibility` in `app/build.gradle.kts`) — these are intentionally different (modern build machine, backward-compatible output).
- **AGP:** 9.4.1. **Kotlin:** 2.4.20. **KSP:** 2.3.12 (Room codegen only; no Hilt/Dagger).
- **Non-standard but required:** root `build.gradle.kts` has `buildscript { dependencies { classpath(libs.kotlin.gradle.plugin) } }` to force Kotlin Gradle Plugin 2.4.20 — AGP 9.4.1's built-in KGP is older (2.2.10) and cannot read metadata compiled with Kotlin 2.4. Do not remove this override without upgrading AGP's bundled KGP.
- **compileSdk** uses the non-integer form `release(37) { minorApiLevel = 2 }` (needed because Compose BOM alpha's `ui` artifact requires compileSdk ≥ 37.1). **minSdk** 33, **targetSdk** 37.
- **Compose BOM:** `compose-bom-alpha:2026.09.01` (not the stable `compose-bom`) — this is what supplies `material3 1.5.0-alpha29` with `ExperimentalMaterial3ExpressiveApi`. Do not swap back to the stable BOM; Expressive components aren't there.
- **Compiler opt-ins** are set module-wide in `app/build.gradle.kts` via `kotlin { compilerOptions { optIn.addAll(...) } }`: `ExperimentalMaterial3Api`, `ExperimentalMaterial3ExpressiveApi`, `kotlinx.serialization.ExperimentalSerializationApi`. No per-file `@OptIn` needed for these three.
- **No package manager beyond Gradle** — this is a single-module Android app (`:app`), no npm/pip/etc. involved.
- **No lint config, no README** — `AGENTS.md` (this file) is the primary contributor-facing doc besides `docs/`. CI is `.github/workflows/android.yml` (GitHub Actions): unit tests + debug APK build on every push/PR to `main`, plus a signed release APK published to a GitHub Release on `v*.*.*` tags.

### Releases & changelog
Release notes on tag push come from `cliff.toml` (git-cliff) via the `release` job in `.github/workflows/android.yml` — not GitHub's auto-generated notes, and not a committed `CHANGELOG.md`. Only `feat`, `fix`, `perf`, and `type!:`/`type(scope)!:` (breaking) Conventional Commits appear, grouped as Features / Bug Fixes / Performance / Breaking Changes; everything else (chore/ci/build/docs/test/refactor/style, non-conventional messages) is silently omitted by design — there's no commit-message enforcement, so not every commit needs a changelog-worthy message. If no commit since the previous tag matches, the release job fails before building rather than publishing an empty changelog. Preview locally before tagging: `npx --yes git-cliff@2.14.2 --unreleased --tag vX.Y.Z --strip all`.

## Testing & QA
- **Framework:** JUnit 4 (`junit:4.13.2`), pure-JVM unit tests only. No Robolectric, no instrumented tests currently implemented (`app/src/androidTest/.../` exists but is empty, despite Espresso/Compose-UI-test dependencies being present in `app/build.gradle.kts`).
- **Scope:** unit tests cover the deterministic transformation layer only — DTO→domain mappers and the RSS parser:
  - `app/src/test/java/.../data/remote/safetygate/SafetyGateMapperTest.kt`
  - `app/src/test/java/.../data/remote/salute/SaluteMapperTest.kt`
  - `app/src/test/java/.../data/remote/salute/MinistryRssParserTest.kt`
- **Fixtures** live in `app/src/test/resources/` (`sg_search.json`, `salute_page_data.json`, `ministry_rss.xml`), loaded via `javaClass.classLoader!!.getResourceAsStream("name")!!` then decoded with the shared `AppJson`. Each fixture deliberately encodes edge cases: null-title fallback, `mainPicture` photo selection, empty brand/photo arrays, a `field_depubblicato=true` (withdrawn) node, a `"Revoca…"` (revocation) node, an unrelated top-level JSON array the streaming decoder must skip, and an RSS item with an empty `<link/>` that must be filtered out.
- **Convention for new tests:** mirror the source package under `app/src/test/java/it/federicorapetti/recalls/...`, name the class `{SourceClass}Test.kt`, put new fixtures in `app/src/test/resources/` with a descriptive snake_case name, assert with plain JUnit `Assert` (no AssertJ/Hamcrest/Truth in use).
- **Run:** `gradlew.bat :app:testDebugUnitTest`.
- **Out of scope for automated tests today:** ViewModels, Compose UI, WorkManager scheduling, Room queries, notification posting — these are verified manually per `docs/MANUAL_TESTING.md` (emulator install + scripted checks, including sqlite3 surgery on the device DB to force new-item notification scenarios). If you add tests in these areas, you'll need to introduce Robolectric or instrumented tests — none of that scaffolding exists yet.
