# AGENTS.md — Memory Map

Guide for AI agents and new contributors. Read this before changing code.
Known problems and planned improvements are tracked in [REVIEW_FINDINGS.md](REVIEW_FINDINGS.md) by ID (for example `BUG-3`).
Check that file before "fixing" something that looks odd: it may already be catalogued, with a suggested fix. When you fix one, add a **Status** line to it and update its heading marker and index row (see the top of that file).
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

- Without `MAPS_API_KEY` the map doesn't load and place search is turned off (tapping the map still works).
- Without `OAUTH_WEB_CLIENT_ID` the build still succeeds, and sign-in says it isn't set up.
- JitPack (`settings.gradle.kts`) only serves the `com.github.chrisbanes` group (PhotoView). A new JitPack dependency needs its group added there.
- Robolectric-based tests (`ColorUtilTest`, `MemoryGroupDaoTest`, …) run against `targetSdk 37` with Robolectric 4.17 (older versions could not start them). They need JDK 21 or newer.
- CI runs the unit tests and lint (`tests.yml`, JDK 21, which Robolectric needs for Android 15 and newer) and Detekt (`detekt.yml`) on every push and PR to master, and each fails the check. Run `./gradlew testDebugUnitTest detekt lintDebug` locally before pushing.
- Detekt only reports issues that aren't in `config/detekt/baseline.xml`. Fix new issues instead of adding them to the baseline; regenerate it (`./gradlew detektBaseline`) only after fixing listed ones, so they don't come back unnoticed.

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
- An item needs re-linking if `deviceId != installationId` or `!isMediaValid` (metadata-only check). `LocalMediaUtil.verifyAndFixMediaItems` matches MediaStore candidates by exact size + signature and updates only `uri` and `deviceId` (`updateMediaLink`), and only while the row still has the URI and signature the run read (a restore during the run can reuse ids). It runs on cold start, after a restore, and from the "Re-link" buttons, always through `MediaRelinker.relink` (never call it directly): runs happen one at a time in `applicationScope`, so they outlive the screen, and requests made during a run share the next one. Each run checks every item again and returns how many are still missing; `hasMissingMedia` answers from the results it cached, so only new or changed items cost a query. Afterwards `deduplicateMediaItems` removes items of one memory that now point to the same photo (same signature), closes the `order` gaps and releases unused grants.
- Never "sweep" all persisted grants that the current database doesn't reference: after restoring an older backup, a newer backup may still need them. Release only the grants of media you just removed (`releaseUriGrantsIfUnused`).
- The "Send logs" zip is written to `cacheDir/logs/`, the only folder the `FileProvider` shares (`res/xml/file_paths.xml`). Keep anything else (backup temp files) out of it.
- "Send logs" also adds `relink_diagnostics.txt` (`RelinkDiagnostics`): why each unlinked item needs re-linking and which files on the phone are near it, with sizes, dates, owners and signatures. It is built when the logs are sent (re-link runs don't pay for it), only reads, and writes its own failure into the file so the logs still go out. Its checks mirror `needsRelink` and the matching rule, so keep the two in step.
- Media from another device is shown as an error tile (type, size, date) and is not loaded.

### Backup and restore (`backup/BackupManager.kt`, `SettingsFragment`)
- Backup: `PRAGMA wal_checkpoint(TRUNCATE)`, copy the database file (and the WAL if not empty) while holding a write transaction (a consistent snapshot), check the copy with `MemoryMapDatabase.withCheckedCopy` (the same open, migration and integrity checks a restore runs), then zip `database.sqlite` (+ `-wal` if still present) + `metadata.json` (`BackupZip`) and upload to the Drive folder "Memory Map Backups" as `MemoryMap_{Automatic|Manual}_Backup_<yyyyMMdd_HHmmss>.zip`. Older backups also contain `-shm`; restore ignores it.
- **Automatic backups** run in the background through WorkManager (`backup/AutomaticBackups.kt`). Every write (save, reorder, preset changes, undo, a final delete) calls `AutomaticBackups.schedule(context)`, which returns at once. It enqueues unique work (`ExistingWorkPolicy.REPLACE`), so a burst of changes leads to one upload 15 seconds after the last one, only with a network, and it survives the screen and the process. `AutomaticBackupWorker` calls `BackupManager.runAutomaticBackup`, which skips when nobody is signed in and otherwise runs `performBackup`. Network and Drive server errors are retried (up to 5 attempts, exponential backoff); missing consent and other errors are not. Settings shows the state as a small inline line (`AutomaticBackups.status`) and refreshes the backup list when a run finishes. Never call `performBackup` for automatic backups directly. Old backups are still never pruned (`DATA-4`).
- Backup and restore share one process-wide `Mutex` in `BackupManager`, so they run one at a time (callers wait for the running one). Each run uses its own temp directory in `cacheDir` (`BackupTempFiles`); leftovers from killed runs are deleted when the next run starts.
- Old backups are never deleted by the app (pruning was deliberately postponed, `DATA-4`). `listBackups` pages through all of them.
- Restore: download, extract only the known entries (`BackupZip`), check `metadata.json` and `database.sqlite` exist, reject backups from a newer database version (`BackupCompatibility`), open the extracted copy with `MemoryMapDatabase.withCheckedCopy` (migrations, schema and integrity checks; rejects a file SQLite would have to create from scratch), read every row (`DatabaseContents`), then replace the live database's contents in one transaction (`DatabaseContentsDao.replaceAll`). The live database is never closed or swapped, Room's Flows pick up the new data, and any failure leaves the current data untouched. The UI then re-links media with whatever media access was granted (the restore never waits for it) and offers full access if photos are still unlinked.
- Android's own Auto Backup and device transfer exclude the database and the DataStore (`res/xml/data_extraction_rules.xml`, `backup_rules.xml`): memories come back only through the Drive backups. Keep both files in sync.
- Auth: sign-in stores the email in DataStore (`USER_EMAIL_KEY`); Drive consent goes through `AuthorizationClient`; API calls build a `GoogleAccountCredential` from the email.

## Conventions

- Run `./gradlew detekt` before committing (CI fails on new issues). `MagicNumber` is active, which is why numbers live in named `private const val`s in a `companion object`.
- In async callbacks use `val binding = _binding ?: return`, and `viewLifecycleOwnerLiveData.value ?: return` instead of `viewLifecycleOwner` (that getter throws when there is no view).
- Register activity result launchers as **property initializers**, never in `onCreateView`: views are recreated on every tab switch, so callbacks would pile up.
- Material date and time pickers come back after a rotation without their listeners. Show each under a tag that says what it is for (unique within its `FragmentManager`) and attach its listener with `setOnPicked` both when showing it and in `onViewCreated`, on the picker found with `findDatePicker` / `findTimePicker` (`util/PickerListeners.kt`). A picker for a list item identifies the item by a stable id, not its position.
- Firebase Performance is used heavily: `@AddTrace` annotations, `PerfUtil.trace("name") { }`, `RoomDatabase.withTransaction("name") { }` (the traced overload in `PerfUtil`). Trace names are `snake_case` (`<class>_<method>`). Don't put `@AddTrace` on hot or per-item functions (helpers called per marker, per pair or per media item): each call starts a trace.
- User-facing strings are currently hard-coded in Kotlin and XML (`I18N-1`). New code should prefer `strings.xml`.
- Generated or local-only files to keep out of commits: `local.properties`, `app/google-services.json`, `app/build/`.

## Gotchas (quick list)

- Hidden tabs have no view, so calling a method on a tab fragment from the activity before its view exists will crash (`BUG-8`).
- `data.MemoryFragment` (entity) vs `fragment.MemoryFragment` (screen): the name clash is handled with `import … as MemoryFragmentEntity` in `MemoryFragmentViewModel`.
- `MemoryFragment.title` (entity) is a mutable `@Ignore` field, filled from the parent group at runtime.
- Deleting a group cascades to fragments and media. The undo (`MainActivity.onMemoryDeleted`) calls `MemoryGroupDao.restoreDeletedGroup`, which re-inserts all three in one transaction. Media URI grants are released, and an automatic backup runs, only when the undo Snackbar is dismissed without "Undo": a released grant can't be taken back, so undo would lose the photos. If the process dies while the Snackbar is showing, those grants stay (harmless, but they count toward the 512 cap).
- `MemoryMapApplication.applicationScope` is for short background work that must outlive the screen that started it (the undo and post-delete clean-up). It logs failures instead of crashing.
- Never call the private backup or restore steps outside `performBackup` / `restoreBackup`: those hold the backup lock. The lock is not reentrant, so don't call one from inside the other.
- `Slider.clearOnSliderTouchListeners()` does not remove change listeners; use `clearOnChangeListeners()` before re-adding one in `onBindViewHolder`.
- Debug builds use the default debug keystore, not the release key. For Google sign-in (and a Maps key restricted to Android apps) to work in debug builds, the debug SHA-1 (`./gradlew signingReport`) must be registered for `com.szabolcshorvath.memorymap` in Google Cloud Console. A debug build can't be installed over a release install: uninstall it first.

## Tests

- Robolectric tests use `@ExtendWith(RobolectricExtension::class)` (JUnit 5).
- DAO tests run on Room under Robolectric (`MemoryGroupDaoTest`). They get DAOs through the `…Internal()` accessors by reflection (`groupDaoForTest()`, `contentsDaoForTest()` in `TestDatabases.kt`), because the traced accessors need a configured FirebaseApp.
- `RestoreCompatibilityTest` builds a database for every schema version a backup can have (6 to `DB_VERSION`) from `app/schemas` (`SchemaDatabases`) and runs it through the restore steps. A new `DB_VERSION` is picked up automatically; it needs the exported schema JSON. Keep this test passing: it is what guarantees old backups stay restorable.
- No dedicated per-migration, hashing, clustering or UI tests yet (`TEST-1`, `TEST-2`, `TEST-3`).
