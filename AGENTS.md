# AGENTS.md — Memory Map

Guide for AI agents and new contributors. Read this before changing code.
Known problems and planned improvements are tracked in [REVIEW_FINDINGS.md](REVIEW_FINDINGS.md) by ID (for example `BUG-3`).
Check that file before "fixing" something that looks odd: it may already be catalogued, with a suggested fix.
Database and schema rules live in `app/src/main/java/com/szabolcshorvath/memorymap/data/AGENTS.md`.
The design of media re-linking is documented in `SIGNATURE_FIX_PLAN.md` (despite the file name).

## What the app is

**Memory Map** is a single-module Android app (Kotlin, XML Views) for saving personal "memories" on a map.
- A **memory** (`MemoryGroup`) has a title, description, location, date range (or all-day), a marker colour (HSV), photos and videos, and optional **fragments**.
- A **fragment** (`data/MemoryFragment`, a Room entity — not an Android Fragment) is an extra location inside a memory, with an optional date range and its own colour. Its title comes from its group.
- Screens: **Map** (clustered markers, date filter, colour stats pie chart), **Timeline** (search, grouped by day), **Add/Edit** memory, **Settings** (Google sign-in, Drive backup and restore, colour presets, map preferences, re-link media, send logs). There is also a memory detail pager and a full-screen media viewer.
- All data is local (Room). Google Drive (`drive.file` scope) is used only for zip backups of the database file.

## Build and run

Local setup needs files that are **not** in git:
- `local.properties` — copy `local.properties.template`.
- `app/google-services.json` — the Firebase config (ignored by git).

> ⚠️ `local.properties` also holds the release signing passwords. `app/build.gradle.kts` reads only the keys it needs: `MAPS_API_KEY` goes to a manifest placeholder and `OAUTH_WEB_CLIENT_ID` to `BuildConfig`. To add a new value, wire it up explicitly in the same way. Never expose the whole file (for example with the Maps Secrets Gradle plugin), and never log `BuildConfig` fields.

- Robolectric-based tests (`ColorUtilTest`) may not start because Robolectric does not support `targetSdk 37` yet (`BUILD-4`). Plain JVM tests run fine.
- CI does **not** run unit tests, and Detekt in CI never fails the build. Run both locally.

## Architecture

### Navigation (`MainActivity`)
- No Navigation component. The four tab fragments (`MapFragment`, `TimelineFragment`, `AddMemoryGroupFragment`, `SettingsFragment`) and `PickLocationFragment` are **all added once** in `onCreate`, with tags (`XxxFragment.TAG`). Switching tabs runs `hide()` + `setMaxLifecycle(CREATED)` on all of them and `show()` + `RESUMED` on the target.
  - Consequence: a hidden fragment **has no view** (`CREATED` destroys it). Every tab switch recreates the view, so restore UI state from the ViewModel in `onViewCreated` (see `ARCH-1`, `PERF-7`).
- Detail screens use the back stack: `MemoryPagerFragment` is opened with `replace` (`onMemoryClicked`), `MediaViewerFragment` with `add` + hide the pager, and `PickLocationFragment` is shown over the Add tab with `addToBackStack`.
- Screens talk to the activity through **listener interfaces** that `MainActivity` implements (`MapListener`, `TimelineListener`, `MemoryFragmentListener`, `AddMemoryGroupListener`, `PickLocationListener`), attached in `onAttach`.
- Cross-tab actions (show on map/timeline, edit) call `selectTab(itemId) { targetFragment.someMethod() }`, which relies on `binding.root.post` running after the tab transaction (`BUG-8`).

### State
- Screen ViewModels mostly hold plain `var` fields: UI state survives rotation but not process death.
- Preferences: a single DataStore named `"googleAuthDatastore"` (it holds all app prefs, not just auth). Keys and defaults are in `util/PreferencesKeys.kt`; the default map filter is in `util/DateFilterOption.kt` (stored by enum `name`).

### Media handling and re-linking (read `SIGNATURE_FIX_PLAN.md`)
- Picked media keep their **photo picker URI**, with `takePersistableUriPermission` (survives reboots, no `READ_MEDIA_*` needed). Android caps persisted grants at 512, so call `LocalMediaUtil.releaseUriGrantsIfUnused` when media is removed.
- Each `MediaItem` stores `deviceId` (the installation id), `fileSize`, `dateTaken` and `mediaSignature` = `"{size}_{md5(first 4 KB + last 4 KB)}"` (`MediaHasher`). The signature is computed once and kept on later saves.
- `InstallationIdentifier` lives in `noBackupFilesDir` on purpose, so Auto Backup and device transfer don't copy it. Don't move it back into DataStore.
- An item needs re-linking if `deviceId != installationId` or `!isMediaValid` (metadata-only check). `LocalMediaUtil.verifyAndFixMediaItems` matches MediaStore candidates by exact size + signature and updates only `uri` and `deviceId` (`updateMediaLink`). It runs on cold start, after a restore, and from the "Re-link" buttons.
- Media from another device is shown as an error tile (type, size, date) and is not loaded.

### Backup and restore (`backup/BackupManager.kt`, `SettingsFragment`)
- Backup: `PRAGMA wal_checkpoint(TRUNCATE)`, then zip `database.sqlite` (+ `-wal`/`-shm` if present) + `metadata.json`, then upload to the Drive folder "Memory Map Backups" as `MemoryMap_{Automatic|Manual}_Backup_<yyyyMMdd_HHmmss>.zip`.
- **Automatic backups** (`triggerAutomaticBackup`) run after almost every write (save, reorder, preset changes, undo) if a user email is stored, and emit `BackupManager.backupEvents` (STARTED/FINISHED) that Settings listens to. They are awaited inline by callers and are not debounced or pruned (`UX-1`, `DATA-3`, `DATA-4`, `PERF-6`).
- Restore: download, unzip, check `metadata.json` exists, reject backups from a newer database version (`BackupCompatibility`), `MemoryMapDatabase.closeDatabase()`, overwrite the database files, then the UI calls `commonViewModel.refreshDatabase()` and re-links media.
- Auth: sign-in stores the email in DataStore (`USER_EMAIL_KEY`); Drive consent goes through `AuthorizationClient`; API calls build a `GoogleAccountCredential` from the email.

## Conventions

- Run `./gradlew detekt` before committing. `MagicNumber` is active, which is why numbers live in named `private const val`s in a `companion object`.
- In async callbacks use `val binding = _binding ?: return`, and `viewLifecycleOwnerLiveData.value ?: return` instead of `viewLifecycleOwner` (that getter throws when there is no view).
- Register activity result launchers as **property initializers**, never in `onCreateView` (`BUG-6`).
- Firebase Performance is used heavily: `@AddTrace` annotations, `PerfUtil.trace("name") { }`, `RoomDatabase.withTransaction("name") { }` (the traced overload in `PerfUtil`). Trace names are `snake_case` (`<class>_<method>`). Don't put `@AddTrace` on hot or per-item functions (`PERF-1`).
- User-facing strings are currently hard-coded in Kotlin and XML (`I18N-1`). New code should prefer `strings.xml`.
- Generated or local-only files to keep out of commits: `local.properties`, `app/google-services.json`, `app/build/`.

## Gotchas (quick list)

- Hidden tabs have no view, so calling a method on a tab fragment from the activity before its view exists will crash (`BUG-8`).
- `CommonRepository.refreshDatabase()` does nothing unless the database was closed (after a restore) (`BUG-10`).
- `data.MemoryFragment` (entity) vs `fragment.MemoryFragment` (screen): the name clash is handled with `import … as MemoryFragmentEntity` in `MemoryFragmentViewModel`.
- `MemoryFragment.title` (entity) is a mutable `@Ignore` field, filled from the parent group at runtime.
- Deleting a group cascades to fragments and media. The undo (`MainActivity.onMemoryDeleted`) calls `MemoryGroupDao.restoreDeletedGroup`, which re-inserts all three in one transaction. Delete does not release media URI grants, which is what keeps undo working: if you add grant release on delete, do it only after the undo Snackbar is gone.
- Backup and restore temp files use fixed paths in `cacheDir` (`backup_temp/`, `backup.zip`, `restore_temp*`), so don't run two at once (`DATA-3`).
- `Slider.clearOnSliderTouchListeners()` does not remove change listeners (`BUG-1`).
- Debug builds use the default debug keystore, not the release key. For Google sign-in (and a Maps key restricted to Android apps) to work in debug builds, the debug SHA-1 (`./gradlew signingReport`) must be registered for `com.szabolcshorvath.memorymap` in Google Cloud Console. A debug build can't be installed over a release install: uninstall it first.

## Tests

- Robolectric tests use `@ExtendWith(RobolectricExtension::class)` (JUnit 5).
- DAO tests run on an in-memory Room database under Robolectric (`MemoryGroupDaoTest`). They get the DAO through `memoryGroupDaoInternal()` by reflection, because the traced `memoryGroupDao()` needs a configured FirebaseApp.
- No Room migration, backup, hashing, clustering or UI tests yet (`TEST-1`, `TEST-2`, `TEST-3`). For migrations, use `MigrationTestHelper` with the schemas in `app/schemas`.
