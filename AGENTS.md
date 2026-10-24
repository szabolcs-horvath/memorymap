# AGENTS.md — Memory Map

Guide for AI agents and new contributors. Read this before changing code.
Known problems and planned improvements are tracked in [REVIEW_FINDINGS.md](REVIEW_FINDINGS.md) by ID (for example `BUG-3`).
Check that file before "fixing" something that looks odd: it may already be catalogued, with a suggested fix.

## What the app is

**Memory Map** is a single-module Android app (Kotlin, XML Views) for saving personal "memories" on a map.
- A **memory** (`MemoryGroup`) has a title, description, location, date range (or all-day), a marker colour (HSV), photos and videos, and optional **fragments**.
- A **fragment** (`data/MemoryFragment`, a Room entity — not an Android Fragment) is an extra location inside a memory, with an optional date range and its own colour. Its title comes from its group.
- Screens: **Map** (clustered markers, date filter, colour stats pie chart), **Timeline** (search, grouped by day), **Add/Edit** memory, **Settings** (Google sign-in, Drive backup and restore, colour presets, map preferences, re-link media, send logs). There is also a memory detail pager and a full-screen media viewer.
- All data is local (Room). Google Drive (`drive.file` scope) is used only for zip backups of the database file.

## Tech stack

| Area | Library / version (see `gradle/libs.versions.toml`) |
|---|---|
| Build | AGP 9.4, Kotlin 2.4 (`kotlin { android { … } }` DSL in `app/build.gradle.kts`), KSP, Gradle 9.8, JDK 17 |
| SDK | `minSdk 27`, `compileSdk`/`targetSdk 37` |
| UI | Fragments + ViewBinding, Material 3 Expressive, RecyclerView/ViewPager2, PhotoView (JitPack), pie-chart (`ir.mahozad`) |
| Maps | Play Services Maps (Advanced Markers, `mapId` in `res/values/strings.xml`), android-maps-utils clustering, Places SDK (new API), Fused Location |
| Data | Room 2.8 (schemas exported to `app/schemas/`), DataStore Preferences |
| Media | Coil 3 (+ `coil-video` for video frames), photo picker (`PickMultipleVisualMedia`) with persisted URI grants |
| Auth / Drive | Credential Manager + Google ID (sign-in), `Identity.getAuthorizationClient` (consent), `GoogleAccountCredential` + Drive v3 client (API calls) |
| Telemetry | Firebase Analytics, Crashlytics, Performance |
| Tests | JUnit 5 (Jupiter), MockK, Robolectric (through the `tech.apter` JUnit 5 extension) |
| Debug | LeakCanary (debug only) |

## Build and run

Local setup needs files that are **not** in git:
- `local.properties` — copy `local.properties.template`. Keys: `sdk.dir`, `MAPS_API_KEY`, `OAUTH_WEB_CLIENT_ID`, and optionally `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- `app/google-services.json` — the Firebase config (ignored by git).

> ⚠️ The Maps Secrets Gradle plugin turns **every** `local.properties` key into a `BuildConfig` field, including the signing passwords (`SEC-1`). Don't add new secrets to `local.properties` until that is fixed, and never log `BuildConfig` fields.

Common commands (Windows: `gradlew.bat`, or `./gradlew` from Git Bash):
```bash
./gradlew assembleDebug
```
```bash
./gradlew testDebugUnitTest
```
```bash
./gradlew detekt
```
- Release builds use R8 (`isMinifyEnabled` + `isShrinkResources`); keep rules are in `app/proguard-rules.pro`.
- Robolectric-based tests (`ColorUtilTest`) may not start because Robolectric does not support `targetSdk 37` yet (`BUILD-4`). Plain JVM tests (`LocalMediaUtilTest`, `TimelineFragmentViewModelTest`) run fine.
- CI (`.github/workflows/`): CodeQL (builds `assembleDebug`) and Detekt (SARIF upload, `continue-on-error`). Unit tests are **not** run in CI.

## Repository layout

```
app/src/main/java/com/szabolcshorvath/memorymap/
├── MainActivity.kt              # Single activity: bottom nav, manual fragment switching, back handling,
│                                #   cold-start checks, undo-delete Snackbar. Defines `Context.dataStore`.
├── MemoryMapApplication.kt      # Maps renderer + Places init, Coil ImageLoader factory
├── adapter/                     # RecyclerView/ViewPager adapters (one per list)
├── auth/GoogleAuthManager.kt    # Credential Manager sign-in/out, GoogleAccountCredential factory
├── backup/BackupManager.kt      # Zip DB → Drive upload, list/restore/delete; automatic backup + BackupEvent flow
├── data/                        # Room entities, DAOs, database, converters, CommonRepository/CommonViewModel
├── fragment/                    # Screens and their ViewModels (XxxFragment + XxxFragmentViewModel)
├── util/                        # Colour, markers, media hashing/re-linking, permissions, perf tracing, prefs keys, logs
└── view/MaxHeightCardView.kt    # Card with maxHeight that always wraps content
app/schemas/…MemoryMapDatabase/  # Exported Room schemas 1..18 (commit new ones)
config/detekt/detekt.yml         # Lint rules (see Conventions)
SIGNATURE_FIX_PLAN.md            # Design doc for media re-linking (despite the file name)
REVIEW_FINDINGS.md               # Catalogue of known issues with IDs
```

## Architecture

### Navigation (`MainActivity`)
- No Navigation component. The four tab fragments (`MapFragment`, `TimelineFragment`, `AddMemoryGroupFragment`, `SettingsFragment`) and `PickLocationFragment` are **all added once** in `onCreate`, with tags (`XxxFragment.TAG`). Switching tabs runs `hide()` + `setMaxLifecycle(CREATED)` on all of them and `show()` + `RESUMED` on the target.
  - Consequence: a hidden fragment **has no view** (`CREATED` destroys it). Every tab switch recreates the view, so restore UI state from the ViewModel in `onViewCreated` (see `ARCH-1`, `PERF-7`).
- Detail screens use the back stack: `MemoryPagerFragment` is opened with `replace` (`onMemoryClicked`), `MediaViewerFragment` with `add` + hide the pager, and `PickLocationFragment` is shown over the Add tab with `addToBackStack`.
- Screens talk to the activity through **listener interfaces** that `MainActivity` implements (`MapListener`, `TimelineListener`, `MemoryFragmentListener`, `AddMemoryGroupListener`, `PickLocationListener`), attached in `onAttach`.
- Cross-tab actions (show on map/timeline, edit) call `selectTab(itemId) { targetFragment.someMethod() }`, which relies on `binding.root.post` running after the tab transaction (`BUG-8`).

### State and data flow
- `CommonRepository` (singleton) holds the database in a `MutableStateFlow` and exposes `allGroups`, `allFragments`, `allPresets` and `getGroupWithMedia(id)` as Flows (`flatMapLatest` over the database, so a restored database can be swapped in with `refreshDatabase`).
- `CommonViewModel` (activity-scoped, `by activityViewModels()`) turns them into `StateFlow`s (`WhileSubscribed(5000)`) and also exposes `showFragmentsEnabled` / `markerClusteringEnabled` from DataStore. It also hands out `getDb()` / DAOs, which UI code uses for direct writes.
- Each screen has its own ViewModel (`by viewModels()`), mostly holding plain `var` fields for UI state that survives rotation (not process death).
- UI collects Flows with `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`.
- Preferences: a single DataStore named `"googleAuthDatastore"` (it holds all app prefs, not just auth). Keys and defaults are in `util/PreferencesKeys.kt`; the default map filter is in `util/DateFilterOption.kt` (stored by enum `name`).

### Database (Room, `data/MemoryMapDatabase.kt`, version **18**)
Tables:
- `memory_groups` (`MemoryGroup`)
- `memory_fragments` (`MemoryFragment`, FK `groupId` → group, `ON DELETE CASCADE`)
- `media_items` (`MediaItem`, FK cascade)
- `hsv_presets` (`HSVPreset`)

Rules when changing the schema:
1. Bump `DB_VERSION`, then add an `AutoMigration` (with a spec for renames or deletes) or a manual `Migration` registered in `getDatabase()`.
2. Commit the new `app/schemas/.../<n>.json`.
3. Remember that **backups are raw database files**: restore runs migrations from older versions, and `metadata.json` records `dbVersion`. A backup from a newer version is not rejected yet, and destructive fallback is currently on (`DATA-1`). Keep migrations complete.
4. Avoid `ALTER TABLE … DROP COLUMN` in manual migrations (it needs SQLite 3.35+, but `minSdk` is 27; see `DATA-9`).

Other data notes:
- Dates are `ZonedDateTime`, stored as ISO strings (`Converters`). All-day memories use the start of the day through `LocalTime.MAX` in the system zone.
- `order` columns (media, fragments, presets) are nullable. Sort by `order`, then fall back to the date (the comparators in `MemoryFragment.kt` and `AddMemoryGroupFragment.kt`).
- `Markerable` is the shared interface of groups and fragments for the map, overlay and clustering (`MarkerableCluster`, `MarkerableDiffCallback`).
- Editing a memory **deletes and re-inserts** its media and fragment rows (their ids change on every save).
- DAOs are wrapped in a reflection proxy that adds a Firebase trace to every call (`PerfUtil.tracedDao`). Always get DAOs through `memoryGroupDao()` / `hsvPresetDao()`, not the `…Internal()` accessors.

### Media handling and re-linking (read `SIGNATURE_FIX_PLAN.md`)
- Picked media keep their **photo picker URI**, with `takePersistableUriPermission` (survives reboots, no `READ_MEDIA_*` needed). Android caps persisted grants at 512, so call `LocalMediaUtil.releaseUriGrantsIfUnused` when media is removed.
- Each `MediaItem` stores `deviceId` (the installation id), `fileSize`, `dateTaken` and `mediaSignature` = `"{size}_{md5(first 4 KB + last 4 KB)}"` (`MediaHasher`). The signature is computed once and kept on later saves.
- `InstallationIdentifier` lives in `noBackupFilesDir` on purpose, so Auto Backup and device transfer don't copy it. Don't move it back into DataStore.
- An item needs re-linking if `deviceId != installationId` or `!isMediaValid` (metadata-only check). `LocalMediaUtil.verifyAndFixMediaItems` matches MediaStore candidates by exact size + signature and updates only `uri` and `deviceId` (`updateMediaLink`). It runs on cold start, after a restore, and from the "Re-link" buttons.
- Media from another device is shown as an error tile (type, size, date) and is not loaded.

### Backup and restore (`backup/BackupManager.kt`, `SettingsFragment`)
- Backup: `PRAGMA wal_checkpoint(TRUNCATE)`, then zip `database.sqlite` (+ `-wal`/`-shm` if present) + `metadata.json`, then upload to the Drive folder "Memory Map Backups" as `MemoryMap_{Automatic|Manual}_Backup_<yyyyMMdd_HHmmss>.zip`.
- **Automatic backups** (`triggerAutomaticBackup`) run after almost every write (save, reorder, preset changes, undo) if a user email is stored, and emit `BackupManager.backupEvents` (STARTED/FINISHED) that Settings listens to. They are awaited inline by callers and are not debounced or pruned (`UX-1`, `DATA-3`, `DATA-4`, `PERF-6`).
- Restore: download, unzip, check `metadata.json` exists, `MemoryMapDatabase.closeDatabase()`, overwrite the database files, then the UI calls `commonViewModel.refreshDatabase()` and re-links media.
- Auth: sign-in stores the email in DataStore (`USER_EMAIL_KEY`); Drive consent goes through `AuthorizationClient`; API calls build a `GoogleAccountCredential` from the email.

### Map rendering (`MapFragment`, `MapFragmentViewModel`, `util/MarkerGenerator`, `util/ColorUtil`)
- `MapFragmentViewModel.getMarkerClusters` combines groups, fragments (if "show fragment markers" is on) and the date filter, then **pre-clusters** items at the same place (`isSameLocationAs`: same name and address, or < 20 m) with union-find. Each pre-cluster is one `ClusterItem`; android-maps-utils clusters further by zoom unless clustering is turned off.
- Single-item pins use `PinConfig` with a contrast glyph colour (`ColorUtil.generateColorWithTargetContrast`, target 2.0). Multi-item pins and clusters are pie-segment bitmaps from `MarkerGenerator` (LRU cache).
- Marker colour is HSV; a null value falls back to `ColorUtil.DEFAULT_MARKER_*`. Prefer the existing `ColorUtil.hsvToColor` helpers.

## Conventions

- **Detekt** (`config/detekt/detekt.yml`, with ktlint formatting): max line 160; `MagicNumber` is active (put numbers in `private const val` in a `companion object`, which is why the codebase is full of named constants); `ReturnCount` max 2; `LongMethod` 60 lines; `LargeClass` 600; `TooManyFunctions` 20; `ComplexCondition` 4; no wildcard imports; `TooGenericExceptionCaught` is active (catch specific exceptions; existing `catch (e: Exception)` blocks predate this). Run `./gradlew detekt` before committing.
- Kotlin official code style; IntelliJ right margin 160.
- ViewBinding pattern: `private var _binding: XBinding? = null` + `private val binding get() = _binding!!`, set to null in `onDestroyView`. In async callbacks use `val binding = _binding ?: return`, and `viewLifecycleOwnerLiveData.value ?: return` instead of `viewLifecycleOwner` (that getter throws when there is no view).
- Register activity result launchers as **property initializers**, never in `onCreateView` (`BUG-6`).
- IO work goes through `withContext(Dispatchers.IO)`; Room DAOs are `suspend` or return `Flow`.
- Each fragment has a `const val TAG` used as its FragmentManager tag and log tag.
- Firebase Performance is used heavily: `@AddTrace` annotations, `PerfUtil.trace("name") { }`, `RoomDatabase.withTransaction("name") { }` (the traced overload in `PerfUtil`). Trace names are `snake_case` (`<class>_<method>`). Don't put `@AddTrace` on hot or per-item functions (`PERF-1`).
- User-facing strings are currently hard-coded in Kotlin and XML (`I18N-1`). New code should prefer `strings.xml`.
- Generated or local-only files to keep out of commits: `local.properties`, `app/google-services.json`, `app/build/`.

## Gotchas (quick list)

- `fallbackToDestructiveMigration(false)` **enables** destructive migration (`DATA-1`).
- Hidden tabs have no view, so calling a method on a tab fragment from the activity before its view exists will crash (`BUG-8`).
- `CommonRepository.refreshDatabase()` does nothing unless the database was closed (after a restore) (`BUG-10`).
- `data.MemoryFragment` (entity) vs `fragment.MemoryFragment` (screen): the name clash is handled with `import … as MemoryFragmentEntity` in `MemoryFragmentViewModel`.
- `MemoryFragment.title` (entity) is a mutable `@Ignore` field, filled from the parent group at runtime.
- Deleting a group cascades to fragments and media. The undo in `MainActivity.onMemoryDeleted` re-inserts only the group and media (`DATA-2`).
- Backup and restore temp files use fixed paths in `cacheDir` (`backup_temp/`, `backup.zip`, `restore_temp*`), so don't run two at once (`DATA-3`).
- `Slider.clearOnSliderTouchListeners()` does not remove change listeners (`BUG-1`).
- Debug builds are signed with the release key when `RELEASE_STORE_FILE` is set (`SEC-2`).

## Tests

- Unit tests: `app/src/test/…`, JUnit 5. Robolectric tests use `@ExtendWith(RobolectricExtension::class)`.
- Existing tests: `ColorUtilTest` (hue normalisation, contrast generation), `LocalMediaUtilTest` (same-file rule), `TimelineFragmentViewModelTest` (search filter).
- No Room migration, backup, hashing, clustering or UI tests yet (`TEST-1`, `TEST-2`, `TEST-3`). For migrations, use `MigrationTestHelper` with the schemas in `app/schemas`.
