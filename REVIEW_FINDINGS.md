# Memory Map — Repository Review Findings

Review date: 2026-10-24 · Commit: `75b0502` (master) · Scope: entire repository (build, CI, data layer, backup, UI, resources, tests).

Each finding has a stable ID (e.g. `SEC-1`) so it can be referenced later ("fix BUG-3").
Severity: **Critical** › **High** › **Medium** › **Low**. Line numbers are as of the commit above.
Status: ✅ fixed · 🟡 partly fixed · ❔ to verify · ⬜ open. The [index](#index) lists every finding with its status and links to it.
When a status changes, update the item's **Status** line, the marker in its heading, its row in the index and the counts above the index.

| Prefix | Area |
|---|---|
| SEC | Security / privacy |
| DATA | Data loss / data integrity |
| BUG | Functional bugs |
| PERF | Performance |
| UX | User experience |
| ARCH | Structure / maintainability |
| BUILD | Build, CI, tooling |
| TEST | Test coverage |
| I18N / A11Y | Localization / accessibility |

---

## Top priorities

| # | | ID | Problem |
|---|---|---|---|
| 1 | ✅ | [SEC-1](#sec-1) | Keystore passwords are compiled into `BuildConfig` and ship in debug APKs. |
| 2 | ✅ | [DATA-1](#data-1) | Destructive migration is *enabled*, so restoring a backup made by a newer app version silently wipes all data. |
| 3 | ✅ | [DATA-2](#data-2) | "Undo" after deleting a memory loses all of its fragments. |
| 4 | ✅ | [DATA-3](#data-3) | Concurrent backups share the same temp files, so backups can corrupt each other. |
| 5 | ✅ | [UX-1](#ux-1) | Saving a memory waits for the full Google Drive upload to finish. |
| 6 | ✅ | [BUG-1](#bug-1) | Fragment colour sliders add a new listener on every rebind (the wrong `clear…` method is called). |
| 7 | ✅ | [BUG-2](#bug-2) | On Android 13+, a failed reverse-geocode leaves "Confirm location" disabled forever (offline location picking is impossible). |
| 8 | ✅ | [PERF-1](#perf-1) | A Firebase trace is started for every pair of markers in the O(N²) clustering loop. |
| 9 | ⬜ | [ARCH-1](#arch-1) | Hidden tabs have their views destroyed on every tab switch (the map is rebuilt each time). |
| 10 | 🟡 | [DATA-4](#data-4) | Automatic backups are never pruned ~~and the backup list is capped at 100 entries~~ (list paging fixed; pruning postponed). |

---

## <a id="index"></a>Index

✅ 49 fixed · 🟡 3 partly fixed · ❔ 0 to verify · ⬜ 26 open

**SEC — Security / privacy** · 8 of 8 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [SEC-1](#sec-1) | Critical | Release signing secrets leak into `BuildConfig` |
| ✅ | [SEC-2](#sec-2) | High | Debug builds are signed with the release key |
| ✅ | [SEC-3](#sec-3) | Medium | Zip-slip check is a plain prefix match |
| ✅ | [SEC-4](#sec-4) | Medium | Restored database is not validated |
| ✅ | [SEC-5](#sec-5) | Low | Unrestricted JitPack repository |
| ✅ | [SEC-6](#sec-6) | Low | FileProvider exposes the whole cache directory |
| ✅ | [SEC-7](#sec-7) | Low | Auto Backup enabled with empty rules |
| ✅ | [SEC-8](#sec-8) | Low | Missing OAuth client ID becomes the string `"null"` |

**DATA — Data loss / integrity** · 7 of 9 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [DATA-1](#data-1) | High | Destructive migration is enabled; restoring a newer backup wipes everything |
| ✅ | [DATA-2](#data-2) | High | Undo of a delete loses the memory's fragments |
| ✅ | [DATA-3](#data-3) | High | Concurrent backups clobber each other's files |
| 🟡 | [DATA-4](#data-4) | Medium | Unbounded automatic backups; list truncated at 100 |
| ✅ | [DATA-5](#data-5) | Medium | Backup copies a live database file; restore is not atomic |
| ✅ | [DATA-6](#data-6) | Medium | Restore closes the database under live users |
| ✅ | [DATA-7](#data-7) | Medium | Deleting a memory never releases URI grants and never backs up |
| ✅ | [DATA-8](#data-8) | Low | `deduplicateMediaItems` silently deletes rows |
| ⬜ | [DATA-9](#data-9) | Low | Early migrations cannot run below Android 14 |

**BUG — Functional bugs** · 16 of 16 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [BUG-1](#bug-1) | High | Slider change listeners accumulate in the fragment editor |
| ✅ | [BUG-2](#bug-2) | High | Location confirm stays disabled forever when geocoding fails (API 33+) |
| ✅ | [BUG-3](#bug-3) | Medium | Date and time pickers stop working after rotation |
| ✅ | [BUG-4](#bug-4) | Medium | Sign-in can leave the Settings screen stuck on "Signing in…" |
| ✅ | [BUG-5](#bug-5) | Medium | Removing a selected photo can crash |
| ✅ | [BUG-6](#bug-6) | Medium | Results registered in `onCreateView` (Settings) |
| ✅ | [BUG-7](#bug-7) | Medium | Leaving the Add tab while saving causes a surprise navigation later |
| ✅ | [BUG-8](#bug-8) | Medium | Tab actions depend on `post()` ordering |
| ✅ | [BUG-9](#bug-9) | Medium | Media signature can differ between reads of the same file |
| ✅ | [BUG-10](#bug-10) | Low | Undo, then a stale `refreshDatabase()` |
| ✅ | [BUG-11](#bug-11) | Low | Thread-unsafe shared `SimpleDateFormat` |
| ✅ | [BUG-12](#bug-12) | Low | Crash paths on unexpected data |
| ✅ | [BUG-13](#bug-13) | Low | Search with no matches says "No memories yet. Add some!" |
| ✅ | [BUG-14](#bug-14) | Low | Places SDK initialized with an empty key |
| ✅ | [BUG-15](#bug-15) | Low | Recycled thumbnails briefly show stale content |
| ✅ | [BUG-16](#bug-16) | Low | Timeline flash can leave the wrong colour |

**PERF — Performance** · 7 of 10 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [PERF-1](#perf-1) | High | Firebase trace inside the O(N²) clustering loop |
| ✅ | [PERF-2](#perf-2) | High | DAO reflection proxy plus a Firebase trace on every DB call |
| ✅ | [PERF-3](#perf-3) | Medium | Save holds a DB write transaction while reading files |
| ✅ | [PERF-4](#perf-4) | Medium | Media picker work runs on the main thread |
| ✅ | [PERF-5](#perf-5) | Medium | Cold start scans all media twice |
| ✅ | [PERF-6](#perf-6) | Medium | Every small change uploads the whole database |
| ⬜ | [PERF-7](#perf-7) | Medium | Map rebuilt on every tab switch or detail open |
| ⬜ | [PERF-8](#perf-8) | Low | Full marker reset on every emission |
| ⬜ | [PERF-9](#perf-9) | Low | Pin icon recomputed on every render |
| ✅ | [PERF-10](#perf-10) | Low | Global layout listener on every layout pass |

**UX — User experience** · 7 of 15 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [UX-1](#ux-1) | High | Save spinner lasts for the whole Drive upload |
| ✅ | [UX-2](#ux-2) | Medium | Automatic backups block the whole Settings screen |
| ✅ | [UX-3](#ux-3) | Medium | Deleting a Drive backup has no confirmation |
| ✅ | [UX-4](#ux-4) | Medium | Restore requires "Allow all" media access |
| ⬜ | [UX-5](#ux-5) | Medium | Location permission asked immediately and repeatedly |
| ✅ | [UX-6](#ux-6) | Medium | Edit mode overwrites an unsaved draft and stays active |
| ⬜ | [UX-7](#ux-7) | Medium | Form state lost on process death |
| ⬜ | [UX-8](#ux-8) | Medium | Full-screen images are decoded at screen size |
| ✅ | [UX-9](#ux-9) | Medium | Map date filter excludes memories that overlap the range |
| ✅ | [UX-10](#ux-10) | Low | Transitive clustering merges distant points |
| ⬜ | [UX-11](#ux-11) | Low | Pickers ignore the locale |
| ⬜ | [UX-12](#ux-12) | Low | Media viewer is not immersive |
| ⬜ | [UX-13](#ux-13) | Low | Edge-to-edge enabled and then cancelled out |
| ⬜ | [UX-14](#ux-14) | Low | Raw technical text shown to users |
| ⬜ | [UX-15](#ux-15) | Low | Placeholder icons and joke version name |

**ARCH — Structure / maintainability** · 1 of 9 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ⬜ | [ARCH-1](#arch-1) | High | Manual fragment management fights the framework |
| ⬜ | [ARCH-2](#arch-2) | High | Business logic lives in Fragments and the Activity |
| ✅ | [ARCH-3](#arch-3) | High | Background work tied to UI scopes |
| ⬜ | [ARCH-4](#arch-4) | Medium | Two Google auth stacks |
| ⬜ | [ARCH-5](#arch-5) | Medium | Duplicated logic |
| 🟡 | [ARCH-6](#arch-6) | Medium | Hand-rolled singletons, no DI |
| ⬜ | [ARCH-7](#arch-7) | Low | Stringly-typed data passing |
| ⬜ | [ARCH-8](#arch-8) | Low | Shared entity mutated off the main thread |
| ⬜ | [ARCH-9](#arch-9) | Low | Dead or misleading code |

**BUILD — Build, CI, tooling** · 3 of 4 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [BUILD-1](#build-1) | Medium | CI never runs the tests |
| ✅ | [BUILD-2](#build-2) | Medium | `MAPS_API_KEY` wiring is inconsistent |
| ⬜ | [BUILD-3](#build-3) | Low | Supply-chain hygiene |
| ✅ | [BUILD-4](#build-4) | Low | Robolectric cannot run against `targetSdk 37` |

**TEST — Coverage gaps** · 0 of 3 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| 🟡 | [TEST-1](#test-1) | High | No Room migration tests |
| ⬜ | [TEST-2](#test-2) | Medium | Critical logic is untested |
| ⬜ | [TEST-3](#test-3) | Low | No UI or instrumentation tests |

**I18N / A11Y** · 0 of 4 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ⬜ | [I18N-1](#i18n-1) | Medium | All UI text is hard-coded |
| ⬜ | [I18N-2](#i18n-2) | Low | RTL disabled |
| ⬜ | [A11Y-1](#a11y-1) | Medium | Missing content descriptions |
| ⬜ | [A11Y-2](#a11y-2) | Low | Colour is the only signal |

---

## SEC — Security / privacy

### <a id="sec-1"></a>✅ SEC-1 · Critical · Release signing secrets leak into `BuildConfig`
- **Where:** `app/build.gradle.kts:7` (secrets plugin), `local.properties`; generated `BuildConfig.java`
- **What:** The Maps Secrets Gradle plugin turns *every* key in `local.properties` into a `BuildConfig` field. The generated `BuildConfig` contains `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_STORE_FILE`. In a local build, the field is present in `app-debug.apk`; R8 strips it from the release APK only because it is unused.
- **Why it matters:** Anyone with a debug APK can read the signing passwords. Together with SEC-2, debug builds are a real distribution risk.
- **Fix:** Move signing secrets out of `local.properties` (a separate `keystore.properties`, or env vars), or configure `secrets { ignoreList.add("RELEASE_.*") }`. Rotate the passwords if a debug APK has ever left the machine.
- **Status:** Fixed. The secrets plugin was removed. `app/build.gradle.kts` now takes only `MAPS_API_KEY` (manifest placeholder) and `OAUTH_WEB_CLIENT_ID` (`BuildConfig`) from `local.properties`. Rotating the passwords is still up to the owner if an older debug APK was ever shared.

### <a id="sec-2"></a>✅ SEC-2 · High · Debug builds are signed with the release key
- **Where:** `app/build.gradle.kts:72-77`
- **What:** `debug { signingConfig = release }`. A debuggable APK signed with the production key can be installed over the Play/production install, and `run-as` then exposes the user's database, DataStore and tokens.
- **Fix:** Use the default debug keystore for debug builds. If a shared signature is needed (for example for OAuth client IDs), register the debug SHA-1 separately instead.
- **Status:** Fixed. The debug build type no longer sets a signing config, so AGP uses the default debug keystore. The debug SHA-1 has to be registered as an Android OAuth client (and on the Maps key, if it is restricted) in Google Cloud Console.

### <a id="sec-3"></a>✅ SEC-3 · Medium · Zip-slip check is a plain prefix match
- **Where:** `backup/BackupManager.kt:256-267`
- **What:** `file.canonicalPath.startsWith(tempRestoreDir.canonicalPath)` also accepts a sibling such as `restore_temp_dir_evil/...`. Directory entries and nested paths are not handled (`FileOutputStream` on a directory throws, which aborts the restore). There are no size limits (zip bomb).
- **Fix:** Compare against `tempRestoreDir.canonicalPath + File.separator`, extract only the three known entry names, and cap the total uncompressed size.
- **Status:** Fixed. `BackupZip.extract` writes only the four known entry names (anything else, directories and `../` paths included, is skipped, so nothing can land outside the target directory) and stops at 1 GiB extracted. Covered by `BackupZipTest`.

### <a id="sec-4"></a>✅ SEC-4 · Medium · Restored database is not validated
- **Where:** `BackupManager.kt:269-303`
- **What:** `verifyBackupMetadata` only checks that `metadata.json` exists. It does not check `dbVersion`, run `PRAGMA integrity_check`, or confirm that the file is SQLite before it overwrites the live database.
- **Fix:** Open the extracted file read-only, check `user_version <= DB_VERSION` and `integrity_check == ok`, then swap it in. See also DATA-1 and DATA-5.
- **Status:** Fixed. Restore requires `database.sqlite`, then opens the extracted copy with `MemoryMapDatabase.withCheckedCopy`: the app's own Room configuration (migrations, schema validation), `PRAGMA integrity_check`, and a guard that rejects the file if SQLite had to create it (Android deletes an unopenable file and SQLite then creates an empty one, which would otherwise have wiped the user's data on restore). Covered by `RestoreCompatibilityTest`.

### <a id="sec-5"></a>✅ SEC-5 · Low · Unrestricted JitPack repository
- **Where:** `settings.gradle.kts:25-27`
- **What:** JitPack is added for every dependency. Any artifact that is not on Google or Maven Central can be resolved from it (dependency-confusion risk). It is only needed for `com.github.chrisbanes:PhotoView`.
- **Fix:** `maven("https://jitpack.io") { content { includeGroup("com.github.chrisbanes") } }`.
- **Status:** Fixed. The JitPack repository in `settings.gradle.kts` only serves the `com.github.chrisbanes` group (PhotoView).

### <a id="sec-6"></a>✅ SEC-6 · Low · FileProvider exposes the whole cache directory
- **Where:** `res/xml/file_paths.xml:3`
- **What:** `<cache-path path=".">` makes everything in `cacheDir` grantable, including backup and restore temp zips and the Coil cache. Only `debug_logs.zip` needs to be shared.
- **Fix:** Write the logs to `cacheDir/logs/` and expose only `path="logs/"`.
- **Status:** Fixed. `LogManager` writes to `cacheDir/logs/` (and deletes the old files in the cache root), and `file_paths.xml` exposes only `logs/`.

### <a id="sec-7"></a>✅ SEC-7 · Low · Auto Backup enabled with empty rules
- **Where:** `AndroidManifest.xml:23-25`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`
- **What:** `allowBackup="true"` with template rule files. The live WAL-mode database, and the DataStore with the user's email, go to Google's Auto Backup and device transfer. This runs alongside the app's own Drive backup and can restore a database whose URIs belong to another phone.
- **Fix:** Decide explicitly. Either exclude the database (the app has its own backup), or keep it and document the behaviour.
- **Status:** Fixed (decision: exclude). `data_extraction_rules.xml` (cloud backup and device transfer) and `backup_rules.xml` (Android 11 and older) exclude the database and the DataStore. Memories come back only through the app's Drive backups. The DataStore is excluded too, so a new phone doesn't start automatic Drive backups of an empty database before the user restores one.

### <a id="sec-8"></a>✅ SEC-8 · Low · Missing OAuth client ID becomes the string `"null"`
- **Where:** `app/build.gradle.kts:41-42`
- **What:** If `OAUTH_WEB_CLIENT_ID` is missing, `BuildConfig.OAUTH_WEB_CLIENT_ID == "null"`, and sign-in fails with an obscure error.
- **Fix:** Fail the build, or default to `""` and show a clear message.
- **Status:** Fixed. `OAUTH_WEB_CLIENT_ID` falls back to the env var, then to `""` (never `"null"`). With an empty value, `GoogleAuthManager.signIn()` throws `SignInNotConfiguredException` and Settings says sign-in isn't set up in this build. The build doesn't fail, so CI runs without secrets still work.

[↑ Back to index](#index)

---

## DATA — Data loss / integrity

### <a id="data-1"></a>✅ DATA-1 · High · Destructive migration is enabled; restoring a newer backup wipes everything
- **Where:** `data/MemoryMapDatabase.kt:103`, `BackupManager.kt:269-303`
- **What:** `fallbackToDestructiveMigration(false)` does **not** disable destructive migration. The argument is `dropAllTables`, and the call turns the fallback *on*. Restoring a backup made with a higher `DB_VERSION` (phone A updated, phone B not) therefore downgrades, and Room silently recreates empty tables. The same happens if a future migration is forgotten.
- **Fix:** Remove the call (the default is to throw on a missing migration). Before restoring, reject backups whose `dbVersion > DB_VERSION`.
- **Status:** Fixed. The fallback call is removed. Restore reads the backup's version from the SQLite file header and from `metadata.json` (`dbVersion` is missing in metadata version 1), and rejects it before touching the live database only if either is newer than `DB_VERSION`. If neither can be read, the restore goes ahead as before. Covered by `BackupCompatibilityTest`.

### <a id="data-2"></a>✅ DATA-2 · High · Undo of a delete loses the memory's fragments
- **Where:** `fragment/MemoryFragment.kt:436-447`, `MainActivity.kt:363-380`
- **What:** `deleteGroup` cascades to `memory_fragments`, but `onMemoryDeleted(group, mediaItems)` only receives and re-inserts the group and media. Fragments are lost after "Undo".
- **Fix:** Pass `MemoryGroupWithMedia` (including fragments) and re-insert the fragments in the same transaction. Better: a soft delete that only becomes permanent when the Snackbar is dismissed.
- **Status:** Fixed. `onMemoryDeleted` receives the whole `MemoryGroupWithMedia`, and undo calls the new `MemoryGroupDao.restoreDeletedGroup` (`@Transaction`), which re-inserts the group, media and fragments. Covered by `MemoryGroupDaoTest`. Soft delete was not implemented.

### <a id="data-3"></a>✅ DATA-3 · High · Concurrent backups clobber each other's files
- **Where:** `BackupManager.kt:86-98, 147`
- **What:** Every backup uses `cacheDir/backup_temp` and `cacheDir/backup.zip`. Automatic backups are started from many places (save, reorder, presets, undo), and a manual backup can run at the same time. `MemoryFragmentViewModel` cancels the previous job, but the blocking Drive `execute()` keeps running. One run's `finally` deletes the zip the other is uploading, or overwrites it mid-upload.
- **Fix:** Use a unique temp dir per run (`createTempFile`) and serialize backups with a `Mutex`, or move them to WorkManager unique work (ARCH-3).
- **Status:** Fixed. Backup and restore share a process-wide `Mutex`, and every run uses its own temp directory (`BackupTempFiles`), with stale leftovers deleted at the start of the next run. Concurrent automatic backups now run one after another instead of in parallel. Saves no longer wait for them at all since UX-1 and PERF-6 moved automatic backups to WorkManager.

### <a id="data-4"></a>🟡 DATA-4 · Medium · Unbounded automatic backups; list truncated at 100
- **Where:** `BackupManager.kt:160-193`
- **What:** Every edit, reorder or preset change uploads a full new zip, and nothing is ever deleted. `files().list()` has no `pageSize` or paging, so after 100 files the oldest (including manual backups) stop showing up. Drive quota grows without limit.
- **Fix:** Keep the last N automatic backups (delete older ones after a successful upload), and page through `nextPageToken`.
- **Status:** Partly fixed. `listBackups` now pages through `nextPageToken` (1000 per page), so every backup is listed. Pruning is postponed by decision: the app still never deletes backups. If it is added later, never delete manual backups, and keep older ones too (for example one per day and one per month), so a bad change cannot quickly rotate out every good backup.

### <a id="data-5"></a>✅ DATA-5 · Medium · Backup copies a live database file; restore is not atomic
- **Where:** `BackupManager.kt:115-157, 278-303`
- **What:** After `wal_checkpoint(TRUNCATE)` the `.sqlite`, `-wal` and `-shm` files are zipped while Room is still open, so a concurrent write can produce an inconsistent snapshot. Restore overwrites the live files in place with no local copy of the current database. A failure halfway through (disk full) leaves a corrupt database and no way back.
- **Fix:** Back up with `VACUUM INTO '<tmp>'` (a single consistent file, no WAL or SHM). Restore by copying to a temp file, validating it (SEC-4), keeping the old database as `.bak`, and then renaming.
- **Status:** Fixed differently. `VACUUM INTO` needs SQLite 3.27 (API 30) but `minSdk` is 27, so backup copies the database file and WAL while holding a write transaction (no write or checkpoint can happen during the copy), checks the copy like a restore would, and zips it. Restore no longer touches the database files at all: it reads every row from the checked copy and replaces the live contents in one transaction (`DatabaseContentsDao.replaceAll`), which rolls back on any failure, so no `.bak` is needed.

### <a id="data-6"></a>✅ DATA-6 · Medium · Restore closes the database under live users
- **Where:** `BackupManager.kt:279`, `SettingsFragment.kt:192-196`, `data/CommonRepository.kt:29-31`
- **What:** `closeDatabase()` runs on the IO thread while `CommonRepository._dbFlow` still holds the closed instance until the UI later calls `refreshDatabase()`. Any database access in that window (an automatic backup, a save, a relink, a Flow re-query) hits a closed pool and throws.
- **Fix:** Put database swapping behind the repository: pause writers, close, swap, reopen and emit, all in one place under a lock.
- **Status:** Fixed by removing the swap. Restore replaces the contents of the open database in a transaction (see DATA-5), so the instance is never closed and Room's Flows re-emit on their own. `closeDatabase()` is gone and `CommonRepository` holds the single instance directly.

### <a id="data-7"></a>✅ DATA-7 · Medium · Deleting a memory never releases URI grants and never backs up
- **Where:** `MemoryFragment.kt:436-447`
- **What:** Editing releases unused persisted grants (`releaseUriGrantsIfUnused`), but delete does not, so grants leak toward Android's 512 cap and older picker items start to lose access. A delete also does not call `triggerAutomaticBackup`, so the newest Drive backup still contains the deleted memory.
- **Fix:** When the undo window ends, release the grants and trigger a backup.
- **Status:** Fixed. When the undo Snackbar is dismissed for any reason other than "Undo" (timeout, swipe, another Snackbar, activity destroyed), `MainActivity` releases the deleted media's grants (if no other memory uses them) and triggers an automatic backup. The undo and this clean-up run in the new `MemoryMapApplication.applicationScope`, so they finish even if the activity goes away. Grants still leak if the process dies while the Snackbar is showing. A startup sweep of unreferenced grants must not be used to close that gap: after restoring an older backup it would release grants that a newer backup still needs (see `releaseUriGrantsIfUnused`).

### <a id="data-8"></a>✅ DATA-8 · Low · `deduplicateMediaItems` silently deletes rows
- **Where:** `util/LocalMediaUtil.kt:261-281`
- **What:** After every relink, items in the same group with the same signature are deleted without telling the user. Their grants are not released and `order` is left with gaps.
- **Fix:** Log or report the count, release the grants, and renumber `order`.
- **Status:** Fixed. `deduplicateMediaItems` logs the removed ids, reports the count in the re-link progress message, deletes the duplicates and closes the `order` gaps in one transaction (`MemoryGroupDao.deleteDuplicateMedia`, which writes only the `order` column), then releases grants no other item uses. A memory with any unordered item is not renumbered, so its displayed order can't change. Covered by `LocalMediaUtilTest` and `MemoryGroupDaoTest`.

### <a id="data-9"></a>⬜ DATA-9 · Low · Early migrations cannot run below Android 14
- **Where:** `MemoryMapDatabase.kt:54-80`
- **What:** `ALTER TABLE … DROP COLUMN` needs SQLite 3.35+ (API 34+), but `minSdk = 27`. Migrations 1→2 and 4→5 would crash on older devices. This only matters for very old installed databases: backups were introduced at schema version 6 (January 4, 2026), so no backup goes through these migrations.
- **Fix:** Rewrite them as create-new-table, copy, drop, rename, or drop support for schema versions below 5.

[↑ Back to index](#index)

---

## BUG — Functional bugs

### <a id="bug-1"></a>✅ BUG-1 · High · Slider change listeners accumulate in the fragment editor
- **Where:** `adapter/MemoryFragmentEditAdapter.kt:294-317`
- **What:** `clearOnSliderTouchListeners()` is called, but the code then adds an **OnChange** listener. Every rebind (and rebinds are frequent: payloads, preset changes, list changes) adds another listener, so one slider movement runs the update N times and can write to whichever position the holder now has.
- **Fix:** Call `clearOnChangeListeners()`, or register the listeners once in the ViewHolder's `init`.
- **Status:** Fixed. `setupColorSection` calls `clearOnChangeListeners()` before adding the change listener, so each slider has exactly one. The listener reads the holder's current position when it fires.

### <a id="bug-2"></a>✅ BUG-2 · High · Location confirm stays disabled forever when geocoding fails (API 33+)
- **Where:** `fragment/PickLocationFragment.kt:255-283`
- **What:** The `GeocodeListener` lambda implements only `onGeocode`. The default `onError` does nothing, so offline or on a service error `setConfirmButtonLoading(false)` is never called and the user cannot confirm a tapped location. A POI-fetch failure falls back to the same path.
- **Fix:** Implement `onError`, and enable "Confirm" right away (coordinates are enough); fill in the name and address when they arrive.
- **Status:** Fixed. Every outcome of the reverse geocode (an address, no result, an exception, or `GeocodeListener.onError` on Android 13+) goes through `showGeocodeResult`, which fills in the address if there is one and re-enables "Confirm". The marker shows "Selected Location" when no name was found. "Confirm" is still disabled while the lookup runs.

### <a id="bug-3"></a>✅ BUG-3 · Medium · Date and time pickers stop working after rotation
- **Where:** `MapFragment.kt:209-235`, `AddMemoryGroupFragment.kt:697-770`, `MemoryFragmentEditAdapter.kt:486-598`
- **What:** `MaterialDatePicker` and `MaterialTimePicker` are restored by the FragmentManager after a configuration change, but their `addOnPositiveButtonClickListener` lambdas are not. Picking a date after rotating does nothing. The fragment-editor pickers also capture `index`, which can point to a different fragment, or be out of range, if the list changes while the dialog is open.
- **Fix:** Re-attach listeners in `onViewCreated` by finding the picker by tag, or use `setFragmentResultListener`. Identify fragments by `localId`, not index.
- **Status:** Fixed. Each picker is shown under a tag that says what it is for, and its listener is attached again in `onViewCreated` to the picker found by that tag, replacing any earlier listener (`util/PickerListeners.kt`). The Add screen and fragment pickers have distinct tags (they share `childFragmentManager`). A fragment picker carries the fragment's `localId`, so the pick goes to that fragment, or nowhere if it was removed meanwhile. After process death the restored pickers apply to the reset form (`UX-7`).

### <a id="bug-4"></a>✅ BUG-4 · Medium · Sign-in can leave the Settings screen stuck on "Signing in…"
- **Where:** `auth/GoogleAuthManager.kt:35-63`, `SettingsFragment.kt:627-675`
- **What:** For an unexpected credential type, `handleSignIn` shows a Toast but neither calls the callback nor throws, so `setLoadingState(false)` never runs and every button stays disabled.
- **Fix:** Throw (or return a result) on the else branches. Also drop the unused `lateinit googleCredential`.
- **Status:** Fixed. `GoogleAuthManager.signIn()` returns the email and throws on an unexpected credential type, so the Settings `catch` always shows the error and clears the loading state. The unused `googleCredential` field is gone.

### <a id="bug-5"></a>✅ BUG-5 · Medium · Removing a selected photo can crash
- **Where:** `adapter/SelectedMediaAdapter.kt:77`, `AddMemoryGroupFragment.kt:288-291`
- **What:** `onRemove(holder.bindingAdapterPosition)` can pass `NO_POSITION` (-1), for example on a quick double tap during the remove animation, and `selectedMedia.removeAt(-1)` throws.
- **Fix:** Ignore `NO_POSITION`, as the other adapters already do.
- **Status:** Fixed. The remove button ignores `NO_POSITION` and passes the item, not its index; the Add screen removes it with `selectedMedia.remove(item)`, so a stale position can't remove a different photo and a second tap does nothing.

### <a id="bug-6"></a>✅ BUG-6 · Medium · Results registered in `onCreateView` (Settings)
- **Where:** `SettingsFragment.kt:131-143`
- **What:** `registerForActivityResult` for the authorization intent runs on every view creation, which happens on every tab switch (ARCH-1). Callbacks pile up, and after process death the result can be delivered to a stale registration and lost.
- **Fix:** Register it once as a property initializer, like the other launchers.
- **Status:** Fixed. `startAuthorizationIntent` is a property initializer.

### <a id="bug-7"></a>✅ BUG-7 · Medium · Leaving the Add tab while saving causes a surprise navigation later
- **Where:** `AddMemoryGroupFragment.kt:257-284`
- **What:** `saveResult` is a buffered Channel collected only while the view is STARTED. If the user switches tabs during a save (which is slow, see UX-1), the Success event waits, and when they next open "Add" the app suddenly jumps to the memory detail. The fields are not cleared until then.
- **Fix:** Handle completion in the ViewModel (clear the form state there), and expose navigation as a one-shot event that is consumed when it is shown.
- **Status:** Fixed. The view model clears the form itself when a save succeeds, so the screen is empty whenever it is shown next. It sends the "open the saved memory" event only while the Add tab is shown (`isScreenShown`, kept up to date in `onHiddenChanged`); a save that finishes after the user switched tabs shows a "Memory saved" toast instead of navigating later. A rotation during the save still navigates, since the tab isn't hidden.

### <a id="bug-8"></a>✅ BUG-8 · Medium · Tab actions depend on `post()` ordering
- **Where:** `MainActivity.kt:167-180, 325-386`
- **What:** `focusOnMemory`, `scrollToAndFlash` and `setEditMode` access `viewLifecycleOwner` or `lateinit adapter` of a fragment whose view only exists after the async tab transaction. It works because `commit()`'s runnable happens to be queued before `root.post`. If the activity is recreated, the fallback `?: MapFragment()` instances are unattached and these calls crash.
- **Fix:** Pass the request as fragment arguments or ViewModel state that the fragment consumes in `onViewCreated`.
- **Status:** Fixed. The actions are requests the target fragment keeps in its ViewModel (`requestFocusOnMemory`, `requestScrollToAndFlash`, `requestEditMode`) and handles once it has a view, right away or in `onViewCreated`; `selectTab` no longer takes an action and nothing is posted. A request stays pending until handled (a view destroyed meanwhile retries it) and a newer one replaces it. Edit mode starts only once the memory has loaded, so a memory deleted meanwhile leaves the form as it was. `MainActivity` looks the fragments up by tag instead of keeping references, so there are no unattached fallback instances.

### <a id="bug-9"></a>✅ BUG-9 · Medium · Media signature can differ between reads of the same file
- **Where:** `util/MediaHasher.kt:39-41, 64-76`
- **What:** A single `read(buffer)` is assumed to fill 4 KB, and `skip()` is assumed to skip everything. Neither is guaranteed for pipe-backed streams (cloud or remote providers, some picker or redaction paths). A short read gives a different hash, and a short skip throws, so re-linking misses exact matches.
- **Fix:** Use `readNBytes` or loop until full, and `pread` / `FileChannel.position()` on the `ParcelFileDescriptor` instead of `skip`. Opening the file once also avoids the second open.
- **Status:** Fixed. `MediaHasher` opens the file once, reads until each 4 KB block is full and moves to the last block with a seek (`FileChannel.position`), falling back to skipping or reading for pipes. Normal files get exactly the same signatures as before. `MediaHasherTest` pins the format and covers short reads, failed skips and unknown sizes.

### <a id="bug-10"></a>✅ BUG-10 · Low · Undo, then a stale `refreshDatabase()`
- **Where:** `CommonRepository.kt:29-31`; call sites in `MainActivity.kt:77`, `MemoryFragment.kt:86`, `SettingsFragment.kt:123,210`
- **What:** Except right after a restore, `getDatabase()` returns the same instance, the StateFlow does not emit, and the call does nothing. It suggests a refresh that never happens.
- **Fix:** Remove these calls, and keep the restore swap in the repository (DATA-6).
- **Status:** Fixed. `refreshDatabase()` and all its call sites are removed (there is no swap any more, see DATA-6).

### <a id="bug-11"></a>✅ BUG-11 · Low · Thread-unsafe shared `SimpleDateFormat`
- **Where:** `util/DateTimeFormatterUtil.kt:10-11`
- **What:** Global `SimpleDateFormat` instances are used from IO threads by concurrent backups and can produce garbled names or timestamps.
- **Fix:** Use `java.time.format.DateTimeFormatter`.
- **Status:** Fixed. The two backup formatters are `java.time.format.DateTimeFormatter`s (thread-safe) with the same patterns and `Locale.US`, applied to `LocalDateTime.now()`, so file names and metadata dates are unchanged. `DateTimeFormatterUtilTest` pins both formats.

### <a id="bug-12"></a>✅ BUG-12 · Low · Crash paths on unexpected data
- **Where:** `DateFilterOption.kt:48` (`valueOf` on a stored preference), `ColorUtil.kt:53-54` (`check` on saturation and brightness from DB or backup), `PickLocationFragment.kt:71` (`!!` on the prediction), `ColorUtil.kt:103` (`error()` if no contrast colour is found)
- **Fix:** Fall back to defaults instead of throwing in UI paths.
- **Status:** Fixed. An unknown stored filter name falls back to the default (`DateFilterOption.ofStoredName`). `hsvToColor` clamps saturation and brightness to 0–1 and uses the defaults for NaN values and a non-finite hue. `generateColorWithTargetContrast` returns black or white, whichever contrasts more, instead of throwing. An autocomplete result without a place is logged and ignored. Covered by `DateFilterOptionTest` and `ColorUtilTest`.

### <a id="bug-13"></a>✅ BUG-13 · Low · Search with no matches says "No memories yet. Add some!"
- **Where:** `TimelineFragment.kt:68-74`, `res/layout/fragment_timeline.xml:61`
- **Fix:** Show "No memories match '<query>'" when the query is not blank.
- **Status:** Fixed. The empty view says `No memories match "<query>"` when the search isn't blank, otherwise "No memories yet. Add some!" (both in `strings.xml`).

### <a id="bug-14"></a>✅ BUG-14 · Low · Places SDK initialized with an empty key
- **Where:** `MemoryMapApplication.kt:31-42`
- **What:** `apiKey != null` passes for `""`, so the SDK initializes and then every request fails.
- **Fix:** Check `isNullOrBlank()` and log clearly.
- **Status:** Fixed. Places is initialized only when the key is not blank (and a missing `metaData` no longer throws); otherwise a warning is logged. Without Places, tapping a POI falls back to the address lookup and the search button shows a message, because `Places.createClient` and the search screen need an initialized SDK.

### <a id="bug-15"></a>✅ BUG-15 · Low · Recycled thumbnails briefly show stale content
- **Where:** `adapter/MediaAdapter.kt:36-61`, `SelectedMediaAdapter.kt:50-75`
- **What:** The error overlay is not reset before a new load, and in the "other device" branch the previous Coil request is not disposed, so it can still finish into the recycled view.
- **Fix:** Call `hideError()` and `thumbnailImage.dispose()` at the start of `bind`.
- **Status:** Fixed. Both thumbnail adapters cancel the view's previous Coil request (`dispose()`) before showing an other-device tile, and hide the error overlay before starting a new load.

### <a id="bug-16"></a>✅ BUG-16 · Low · Timeline flash can leave the wrong colour
- **Where:** `adapter/TimelineAdapter.kt:117-128`
- **What:** The "original" colour is read from the card. A second flash during the first one, or recycling mid-animation, keeps the highlight colour.
- **Fix:** Use the theme surface colour as the base and cancel the animator in `onViewRecycled`.
- **Status:** Fixed. The card's colour is read once when the view holder is created. A new flash cancels the running one, and `onViewRecycled` cancels it and restores the card colour.

[↑ Back to index](#index)

---

## PERF — Performance

### <a id="perf-1"></a>✅ PERF-1 · High · Firebase trace inside the O(N²) clustering loop
- **Where:** `data/Markerable.kt:26`, `fragment/MapFragmentViewModel.kt:114-144`
- **What:** `isSameLocationAs` has `@AddTrace` and is called for every pair: 500 markers means about 125,000 trace start/stop calls on every recompute (each filter or data change). Each call also runs `Location.distanceBetween`.
- **Fix:** Remove `@AddTrace` from hot helpers (also `isMediaValid`, `generateColorWithTargetContrast` and the per-marker renderer callbacks). Use a grid or geohash bucket so only neighbours are compared.
- **Status:** Fixed. `@AddTrace` is removed from `isSameLocationAs`, `isMediaValid`, `generateColorWithTargetContrast`, the three `MarkerGenerator` icon functions and the four per-marker renderer callbacks. `clusterMarkerables` and `updatePieChart` keep theirs (one per recompute). The pairwise comparison is gone (with UX-10): `MarkerClustering` keeps each pin's anchor in a grid of 100 m cells, so a marker is compared only with the anchors in the cells around it, which is linear in the number of markers for real data. `isSameLocationAs` no longer exists.

### <a id="perf-2"></a>✅ PERF-2 · High · DAO reflection proxy plus a Firebase trace on every DB call
- **Where:** `util/PerfUtil.kt:34-86`, `MemoryMapDatabase.kt:43-45`
- **What:** Every DAO call goes through `java.lang.reflect.Proxy` and creates a Firebase trace. For Flow-returning methods the trace measures only Flow creation, so the data is meaningless. Firebase also rate-limits custom traces.
- **Fix:** Drop the proxy. Firebase Perf already captures app start and screen rendering; trace a few high-level operations (save, backup, restore) by hand.
- **Status:** Fixed. The proxy is gone: `memoryGroupDao()`, `hsvPresetDao()` and `databaseContentsDao()` are Room's own accessors, so DAO calls are plain calls with no trace each. A save is traced as a whole (`add_memory_group_view_model_save_memory_group`, around the existing transaction trace); backup and restore already trace each step. Tests use the DAOs directly instead of reaching the untraced ones by reflection.

### <a id="perf-3"></a>✅ PERF-3 · Medium · Save holds a DB write transaction while reading files
- **Where:** `AddMemoryGroupFragmentViewModel.kt:103-114, 173-243`
- **What:** `saveMediaItems` runs MediaStore queries and `MediaHasher` (file I/O, which can download cloud files) **inside** `withTransaction`, blocking every other writer for the whole time.
- **Fix:** Compute metadata and signatures before opening the transaction, ideally right after picking.
- **Status:** Fixed. `prepareMediaItems` fills in missing sizes, dates and signatures (file reads, possibly a cloud download) before `withTransaction`; the transaction only writes the group, media and fragments. Signatures are still computed at save time, not right after picking.

### <a id="perf-4"></a>✅ PERF-4 · Medium · Media picker work runs on the main thread
- **Where:** `AddMemoryGroupFragment.kt:87-131`
- **What:** `contentResolver.getType` and `query` for every picked URI run in `lifecycleScope.launch` on Main, and `takePersistableUriPermission` runs in the callback. Picking dozens of items causes jank or ANR risk.
- **Fix:** `withContext(Dispatchers.IO)`.
- **Status:** Fixed. Persisting the URI grants, reading the installation id and the per-item type, size and date queries run in one `Dispatchers.IO` block; only the list update runs on the main thread.

### <a id="perf-5"></a>✅ PERF-5 · Medium · Cold start scans all media twice
- **Where:** `MainActivity.kt:258-265`, `LocalMediaUtil.kt:33-41, 105-131`
- **What:** `verifyAndFixMediaItems` runs `isMediaValid` (one ContentResolver IPC per item) for every media item on every launch, then `hasMissingMedia` repeats it for all items. Each detail page (`MemoryFragment.displayMediaItems`) repeats it again on every emission.
- **Fix:** Check once per session and cache the result, check only after an install, restore or permission change, and return the invalid set from the first pass.
- **Status:** Fixed. `verifyAndFixMediaItems` returns how many items are still missing (`RelinkResult`), so cold start no longer checks every item a second time. `hasMissingMedia` (detail page, after a restore) answers from a per-process cache keyed by everything the check reads (id, URI, device id, size, date), so only new or changed items are checked. Each re-link run (cold start, restore, permission grant, the Re-link buttons) checks every item again and replaces the cache. A photo deleted from the gallery while the app runs is noticed at the next run.

### <a id="perf-6"></a>✅ PERF-6 · Medium · Every small change uploads the whole database
- **Where:** `MemoryFragmentViewModel.kt:34-54`, `SettingsFragmentViewModel.kt:45-51`, `SettingsFragment.kt:425-510`, `AddMemoryGroupFragmentViewModel.kt:117`
- **What:** Each drag-reorder, preset tweak or save triggers a full zip and upload, with no debouncing, network constraint or battery constraint.
- **Fix:** Debounced WorkManager unique work with `NetworkType.CONNECTED` (see ARCH-3).
- **Status:** Fixed. Automatic backups are unique WorkManager work with `ExistingWorkPolicy.REPLACE`, a 15-second delay and `NetworkType.CONNECTED`: a burst of changes leads to one upload. Network and Drive server errors are retried with exponential backoff (up to 5 attempts). No battery constraint: one upload of the database is small.

### <a id="perf-7"></a>⬜ PERF-7 · Medium · Map rebuilt on every tab switch or detail open
- **Where:** `MainActivity.kt:99-110, 156-164, 296-302`
- **What:** See ARCH-1. `setMaxLifecycle(CREATED)` destroys the hidden fragments' views, and `replace()` in `onMemoryClicked` removes *all* tab fragments. Each return to the Map recreates `SupportMapFragment`, the GoogleMap, the ClusterManager and all markers.
- **Fix:** Keep tab views alive (`STARTED`, not `CREATED`) or use Navigation with multiple back stacks. Open details with `add` + `hide` instead of `replace`.

### <a id="perf-8"></a>⬜ PERF-8 · Low · Full marker reset on every emission
- **Where:** `MapFragment.kt:503-566`
- **What:** `clearItems()` + `addItems()` + `cluster()` re-renders every marker, even for an unrelated change such as a title edit elsewhere.
- **Fix:** Diff the items, or update only the changed ones (`updateItem`).

### <a id="perf-9"></a>⬜ PERF-9 · Low · Pin icon recomputed on every render
- **Where:** `util/MarkerGenerator.kt:59-69`
- **What:** `singleColorPinConfigIcon` runs a 20-step binary search and builds a new `BitmapDescriptor` per marker per render, uncached. The multi-colour cache key uses `colors.hashCode()` (collisions give wrong icons).
- **Fix:** Cache by colour int, and use `colors.joinToString()` as the key.

### <a id="perf-10"></a>✅ PERF-10 · Low · Global layout listener on every layout pass
- **Where:** `MapFragment.kt:387-389`
- **What:** `addOnGlobalLayoutListener { setGoogleMapPadding() }` is never removed and runs `setPadding` on every layout pass of the whole tree.
- **Fix:** Use `doOnLayout` or `addOnLayoutChangeListener` on `overlayCard` only.
- **Status:** Fixed. The global layout listener is replaced by `OnLayoutChangeListener`s on `overlayCard` and `dateFilterContainer` that update the padding only when one of them changes size or position.

[↑ Back to index](#index)

---

## UX — User experience

### <a id="ux-1"></a>✅ UX-1 · High · Save spinner lasts for the whole Drive upload
- **Where:** `AddMemoryGroupFragmentViewModel.kt:116-118`
- **What:** `triggerAutomaticBackup` is awaited before `SaveResult.Success` is sent. Saving takes as long as zip + auth + upload; offline it waits for network timeouts.
- **Fix:** Send Success right after the DB transaction, then start the backup in the background.
- **Status:** Fixed. Saving schedules the automatic backup (`AutomaticBackups.schedule`) and sends `SaveResult.Success` right after the database transaction; the upload runs later in the background.

### <a id="ux-2"></a>✅ UX-2 · Medium · Automatic backups block the whole Settings screen
- **Where:** `SettingsFragment.kt:236-253`
- **What:** `BackupEvent.STARTED` shows the full loading overlay and disables every control, including presets, sign-out and send logs, for each automatic backup.
- **Fix:** Show a small inline "Backing up…" indicator instead.
- **Status:** Fixed. Automatic backups no longer touch the loading overlay. Settings shows a small inline line under "Backup Now" (pending, backing up, or last backup failed) from the WorkManager state, and refreshes the backup list quietly when a run finishes.

### <a id="ux-3"></a>✅ UX-3 · Medium · Deleting a Drive backup has no confirmation
- **Where:** `adapter/BackupAdapter.kt:57`, `SettingsFragment.kt:1023-1028`
- **What:** One tap permanently deletes the backup. Restore asks for confirmation; delete does not.
- **Fix:** Add a confirmation dialog.
- **Status:** Fixed. Deleting a backup asks first, naming the backup and saying it cannot be undone.

### <a id="ux-4"></a>✅ UX-4 · Medium · Restore requires "Allow all" media access
- **Where:** `SettingsFragment.kt:984-991, 88-107`
- **What:** On Android 14+, choosing "Selected photos" blocks the restore entirely ("Please retry the restore…"), although the database restore needs no permission and re-linking works with partial access (as the rest of the app assumes).
- **Fix:** Always restore, then re-link with whatever access exists and offer "Grant full access to re-link more".
- **Status:** Fixed. The permission request before a restore (with its explanation on Android 14+) no longer blocks it: the restore runs whatever is granted. After re-linking, if access isn't full and photos are still unlinked, a dialog offers "Allow access", which re-links again once granted.

### <a id="ux-5"></a>⬜ UX-5 · Medium · Location permission asked immediately and repeatedly
- **Where:** `MapFragment.kt:117-123, 455-471`, `PickLocationFragment.kt:164-167, 375-385`
- **What:** The system dialog appears on first launch with no explanation, and again each time the map or picker resumes in a new fragment instance (the "denied" flag lives only in the fragment or ViewModel).
- **Fix:** Ask when the user taps "my location", show a rationale, and persist the denial.

### <a id="ux-6"></a>✅ UX-6 · Medium · Edit mode overwrites an unsaved draft and stays active
- **Where:** `AddMemoryGroupFragment.kt:522-591`, `MainActivity.kt:382-386`
- **What:** Tapping "Edit" on a memory silently replaces any in-progress new memory. If the user leaves without saving, the "Add" tab still shows the edit form ("Update") next time.
- **Fix:** Ask before discarding a dirty draft, and keep "new" and "edit" separate (for example, edit as its own screen).
- **Status:** Fixed. The form knows whether it has unsaved changes: `hasUnsavedChanges` compares what a save would store with the content when the form was last cleared or loaded for editing (`FormContent`; expanding a section is not a change). An Edit request on a changed form first asks "Discard the new memory?" or "Discard your changes?"; "Keep editing" leaves the form as it is, and the question comes back if the screen is recreated while it is shown. Editing the memory that is already open with changes keeps them. Switching to another tab closes an edit without changes, so the Add tab offers a new memory next time; an edit with changes is kept like a draft, on purpose. Edit as its own screen is left for the navigation rework (ARCH-1). Covered by `AddMemoryGroupFragmentViewModelTest`.

### <a id="ux-7"></a>⬜ UX-7 · Medium · Form state lost on process death
- **Where:** `AddMemoryGroupFragmentViewModel.kt:44-69`, other ViewModels
- **What:** The whole draft (title, media, fragments, dates) lives in plain ViewModel fields, not `SavedStateHandle`. Opening the photo picker or switching apps can kill the process and lose it.
- **Fix:** Persist the draft through `SavedStateHandle` or a draft table.

### <a id="ux-8"></a>⬜ UX-8 · Medium · Full-screen images are decoded at screen size
- **Where:** `adapter/MediaPagerAdapter.kt:58-61, 144-149`
- **What:** `size(screenWidth, screenHeight)` limits resolution, so pinch-zoom in PhotoView only upscales a blurry bitmap.
- **Fix:** Load at `Size.ORIGINAL` (or tile with subsampling) when zoomed.

### <a id="ux-9"></a>✅ UX-9 · Medium · Map date filter excludes memories that overlap the range
- **Where:** `MapFragmentViewModel.kt:94-101`
- **What:** A memory shows only if it is fully inside the range. A Dec 28–Jan 3 trip disappears under "This Year" and "Last 7 days".
- **Fix:** Use overlap semantics: `start <= filterEnd && end >= filterStart`.
- **Status:** Fixed. The map shows a memory or fragment if any part of it falls within the selected range (`MapFragmentViewModel.overlapsDateFilter`, covered by `MapFragmentViewModelTest`), so a Dec 28 – Jan 3 trip shows under "This Year". Showing a memory on the map widens the filter only when the memory lies entirely outside it.

### <a id="ux-10"></a>✅ UX-10 · Low · Transitive clustering merges distant points
- **Where:** `MapFragmentViewModel.kt:114-144`, `Markerable.kt:27-35`
- **What:** Union-find chains points: A–B and B–C under 20 m makes A–C one pin even if they are 40 m+ apart, and the pin sits at the *first* item's location. Matching on name + address alone merges far-apart places with the same label.
- **Fix:** Anchor the threshold to a centroid, and require distance as well as the name.
- **Status:** Fixed. `MarkerClustering` gives each pin an anchor: the first marker, in list order, that fit no existing pin. A marker joins the nearest pin whose anchor is under 20 m away, or under 100 m when both have the same place name and address (same label alone no longer merges). Distances are measured to the anchor, never between members, so pins don't chain: two markers of one pin are under 40 m apart (200 m for the same place). The anchor stays fixed rather than following the centroid, which would let earlier members drift out of range; the pin is drawn at the centroid of its markers (across the 180th meridian too). Covered by `MarkerClusteringTest`, which also checks random markers against the rules one by one.

### <a id="ux-11"></a>⬜ UX-11 · Low · Pickers ignore the locale
- **Where:** `AddMemoryGroupFragment.kt:750-751`, `MemoryFragmentEditAdapter.kt:569-570`, `DateFilterOption.kt:15`, `MediaAdapter.kt:78`
- **What:** 24-hour clock forced, "This week" always starts on Monday, and a fixed `yyyy.MM.dd` format in the media error overlay.
- **Fix:** Use `DateFormat.is24HourFormat(context)`, `WeekFields.of(locale)` and localized formatters.

### <a id="ux-12"></a>⬜ UX-12 · Low · Media viewer is not immersive
- **Where:** `MainActivity.kt:304-319`, `res/layout/fragment_media_viewer.xml`
- **What:** The bottom navigation and system bars stay visible, there is no tap-to-hide, and tapping a tab pops the viewer.
- **Fix:** Hide the bottom navigation and system bars while viewing.

### <a id="ux-13"></a>⬜ UX-13 · Low · Edge-to-edge enabled and then cancelled out
- **Where:** `MainActivity.kt:84`, `res/layout/activity_main_container.xml:6`
- **What:** `enableEdgeToEdge()` plus `fitsSystemWindows="true"` on the root pads everything, so the map never draws behind the system bars.
- **Fix:** Apply insets per view (map padding, bottom navigation).

### <a id="ux-14"></a>⬜ UX-14 · Low · Raw technical text shown to users
- **Where:** `SettingsFragmentViewModel.kt:65,84,101` (exception class names), `MapFragment.kt:629` ("Lat: %.4f…"), `MemoryFragment.kt:350`, `MemoryFragmentAdapter.kt:61` (raw coordinates)
- **Fix:** Use friendly messages and formatted coordinates.

### <a id="ux-15"></a>⬜ UX-15 · Low · Placeholder icons and joke version name
- **Where:** `res/menu/bottom_nav_menu.xml` (`@android:drawable/ic_dialog_map` and similar), `app/build.gradle.kts:35` (`versionName = "Debugolunk, debugolunk"`)
- **What:** Legacy framework drawables look out of place in Material 3, and the version name is shown in system settings and the store.
- **Fix:** Use Material Symbols vector icons and semantic versioning.

[↑ Back to index](#index)

---

## ARCH — Structure / maintainability

### <a id="arch-1"></a>⬜ ARCH-1 · High · Manual fragment management fights the framework
- **Where:** `MainActivity.kt` (whole file)
- **What:** Tabs are added up front, then hidden *and* capped at `CREATED`, which destroys their views, so `hide()` is redundant and every switch rebuilds the screen. Details use `replace()`, which tears down all tabs. Cross-screen calls go through `lateinit` fragment references plus `post()`. This causes BUG-6, BUG-8 and PERF-7.
- **Fix:** Navigation component with bottom-nav multiple back stacks (or `STARTED` + `hide` without `replace`). Pass requests as arguments.

### <a id="arch-2"></a>⬜ ARCH-2 · High · Business logic lives in Fragments and the Activity
- **Where:** `SettingsFragment.kt` (1041 lines), `AddMemoryGroupFragment.kt` (786), `MapFragment.kt` (778), `MainActivity.kt`
- **What:** DAO writes (preset CRUD, delete, undo transaction), backup triggers, relink orchestration and permission flows are written inline in UI classes. `CommonViewModel` hands raw `getDb()` and `getMemoryGroupDao()` to the UI. ViewModels expose mutable `var`s and a `MutableList` shared directly with an adapter (`viewModel.fragments` → `MemoryFragmentEditAdapter`).
- **Fix:** Add repository and use-case classes (`MemoryRepository.save/delete/restore`, `BackupScheduler`, `MediaRelinker`). ViewModels expose immutable `StateFlow<UiState>` and take intents.

### <a id="arch-3"></a>✅ ARCH-3 · High · Background work tied to UI scopes
- **Where:** All `triggerAutomaticBackup` callers; relink in `MainActivity` / `MemoryFragment` / `SettingsFragment`
- **What:** Backups and relinks run in `lifecycleScope` or `viewModelScope`. They are cancelled or lost when the screen goes away or the process dies, and nothing coordinates or deduplicates them (DATA-3, PERF-6).
- **Fix:** WorkManager unique work (`enqueueUniqueWork("auto-backup", REPLACE)` with a short initial delay and a network constraint) and a single `MediaRelinker` with a mutex.
- **Status:** Fixed. Automatic backups run in WorkManager (`AutomaticBackupWorker`), so they survive the screen and the process and are deduplicated. Media re-linking goes through `MediaRelinker.relink`: runs happen one at a time (a `Mutex`) in `MemoryMapApplication.applicationScope`, so they finish when the screen goes away, and requests made during a run share the next run (`SerialRunner`, covered by `SerialRunnerTest`). Screens only wait for the result and show its progress. A run killed with the process is not resumed; the next cold start runs it again.

### <a id="arch-4"></a>⬜ ARCH-4 · Medium · Two Google auth stacks
- **Where:** `auth/GoogleAuthManager.kt`, `SettingsFragment.kt:924-974`, `BackupManager.kt:44-58`
- **What:** Credential Manager is used for sign-in and `AuthorizationClient` for consent, but Drive calls use the legacy `GoogleAccountCredential.usingOAuth2` + account name (AccountManager-based). The access token from `AuthorizationResult` is thrown away. `UserRecoverableAuthIOException` from `listBackups` is swallowed as "no backups".
- **Fix:** Use `AuthorizationClient` for tokens everywhere (silent `authorize()` for automatic backups) and surface recoverable auth errors.

### <a id="arch-5"></a>⬜ ARCH-5 · Medium · Duplicated logic
- **What and where:**
  - Relink trigger + progress UI: `MainActivity.kt:70-81`, `MemoryFragment.kt:51-89`, `SettingsFragment.kt:77-128, 192-213`
  - HSV slider + preset + animation UI ×3: `AddMemoryGroupFragment`, `MemoryFragmentEditAdapter`, `SettingsFragment`
  - Date and time picker logic ×2: `AddMemoryGroupFragment`, `MemoryFragmentEditAdapter`
  - Media/fragment comparators ×2: `MemoryFragment.kt:371-411`, `AddMemoryGroupFragment.kt:593-624`
  - `getFormattedDate` ×2: `MemoryGroup.kt`, `MemoryFragment.kt` (data)
  - `showError` ×2: `MediaAdapter`, `SelectedMediaAdapter`
  - `hsvToColor(h ?: DEFAULT…, s ?: …, v ?: …)` about 15×; add `Markerable.markerColor()`
  - Location permission handling ×2: `MapFragment`, `PickLocationFragment`
  - Selected-media metadata query ×2: `AddMemoryGroupFragment.kt:104-115`, `AddMemoryGroupFragmentViewModel.kt:194-215`
- **Fix:** Extract shared components (a `ColorPickerView`, a `DateRangeEditor`) and helpers.

### <a id="arch-6"></a>🟡 ARCH-6 · Medium · Hand-rolled singletons, no DI
- **Where:** `CommonRepository`, `MemoryMapDatabase`, `InstallationIdentifier`, `BackupManager(context)` created per fragment, `GoogleAuthManager` per call
- **What:** `MemoryMapDatabase.getDatabase` has no second null check inside `synchronized`, so two threads can build two databases (`MemoryMapDatabase.kt:94-107`). The DAO cache fields are not volatile.
- **Fix:** Hilt or Koin (or at least a small `AppContainer` in `Application`), and fix the double-checked locking.
- **Status:** Partly fixed: `MemoryMapDatabase.getDatabase` now re-checks inside `synchronized`. No DI yet.

### <a id="arch-7"></a>⬜ ARCH-7 · Low · Stringly-typed data passing
- **Where:** `MemoryFragmentListener.onMediaClick(ArrayList<Pair<String,String>>)`, `MediaPagerAdapter` (`item.second == "VIDEO"`), `MediaViewerFragment` (Serializable `Pair` list in arguments, which also risks `TransactionTooLargeException` for large memories)
- **Fix:** Pass the memory id + start index and load the media in the viewer's ViewModel.

### <a id="arch-8"></a>⬜ ARCH-8 · Low · Shared entity mutated off the main thread
- **Where:** `MapFragmentViewModel.kt:89`, `data/MemoryFragment.kt:47-48`
- **What:** `fragment.title = parent.title` mutates Room-emitted objects on `Dispatchers.Default`, and those objects are shared with other collectors. `title` is a `@Ignore var` that is not part of `equals`.
- **Fix:** Map to an immutable UI model (`MarkerItem(title, …)`).

### <a id="arch-9"></a>⬜ ARCH-9 · Low · Dead or misleading code
- `AddMemoryGroupFragmentViewModel.isInitialized` (written, never read)
- The `IS_FIRST_RUN` / `LAST_APP_VERSION` block in `MainActivity.kt:244-256` writes the version twice and reads it nowhere
- The commented-out passkey and password branches in `GoogleAuthManager.kt`
- `ExampleUnitTest` and `ExampleInstrumentedTest`
- The DataStore is named `"googleAuthDatastore"` but holds all app preferences
- The default literals in `SettingsFragment.kt:290,313` duplicate the `PreferencesKeys` defaults
- `SIGNATURE_FIX_PLAN.md` now documents media re-linking, not a "fix plan"; move it to `docs/media-relinking.md`
- `ColorPresetAdapter.setSelectedPresetId` is never called from the Add screen, so the chosen preset is never highlighted there

[↑ Back to index](#index)

---

## BUILD — Build, CI, tooling

### <a id="build-1"></a>✅ BUILD-1 · Medium · CI never runs the tests
- **Where:** `.github/workflows/*.yml`
- **What:** Only CodeQL (which builds debug) and Detekt run. Detekt has `continue-on-error: true`, so it never fails a PR. The `baseline.xml` referenced in `build.gradle.kts:25` does not exist.
- **Fix:** Add a `./gradlew testDebugUnitTest lintDebug` job, make Detekt blocking (generate a baseline first).
- **Status:** Fixed. The new "Tests" workflow (`tests.yml`) runs `testDebugUnitTest lintDebug` on JDK 21 for every push and PR to master, and uploads the reports when it fails. Detekt no longer has `continue-on-error`. The existing issues are in `config/detekt/baseline.xml`, so only new ones fail the check.

### <a id="build-2"></a>✅ BUILD-2 · Medium · `MAPS_API_KEY` wiring is inconsistent
- **Where:** `app/build.gradle.kts:39` vs the secrets plugin vs CI (`local.properties`)
- **What:** The manifest placeholder is set from an env var (empty in CI and locally), while the secrets plugin also injects it from `local.properties`. Which one wins depends on plugin ordering.
- **Fix:** Pick one source (the secrets plugin with an explicit `propertiesFileName` and an `ignoreList`), and remove the other.
- **Status:** Fixed as part of SEC-1. The secrets plugin is gone; the placeholder comes from `local.properties`, falling back to the env var.

### <a id="build-3"></a>⬜ BUILD-3 · Low · Supply-chain hygiene
- **What:** GitHub Actions are pinned to tags (`@v4`) rather than SHAs; `gradle-wrapper.properties` has no `distributionSha256Sum`.

### <a id="build-4"></a>✅ BUILD-4 · Low · Robolectric cannot run against `targetSdk 37`
- **Where:** noted in `SIGNATURE_FIX_PLAN.md` §5
- **What:** `ColorUtilTest` does not start, so the only colour tests are effectively off.
- **Fix:** Pin `@Config(sdk = [36])` for Robolectric tests, or make `ColorUtil` pure-JVM (its maths does not need Android).
- **Note (2026-10-24):** Could not reproduce. With Robolectric 4.17, `ColorUtilTest` (91 tests) and `MemoryGroupDaoTest` run and pass locally. Probably resolved by a dependency upgrade; close it after a CI run confirms.
- **Status:** Fixed (2026-10-25) by the Robolectric 4.17 upgrade, no code change needed. The "Tests" CI workflow runs all unit tests, including the Robolectric ones, against `targetSdk 37` on JDK 21, and they pass.

[↑ Back to index](#index)

---

## TEST — Coverage gaps

### <a id="test-1"></a>🟡 TEST-1 · High · No Room migration tests
- **What:** 18 schema versions, 4 manual migrations (two of them broken below API 34, DATA-9) and the destructive fallback (DATA-1), with no `MigrationTestHelper` test. The exported schemas are already in `app/schemas`.
- **Status:** Partly covered. `RestoreCompatibilityTest` builds a database of every version a backup can have (6 to `DB_VERSION`) from `app/schemas`, migrates it with the app's configuration and checks the data. Versions 1 to 5 (only very old installs, never backups) and per-migration edge cases are still untested.

### <a id="test-2"></a>⬜ TEST-2 · Medium · Critical logic is untested
- **What:** No tests for `MediaHasher` (BUG-9), backup zip/unzip/restore (SEC-3, SEC-4, DATA-5), the clustering and date filter in `MapFragmentViewModel` (UX-9, UX-10), `saveMediaItems` dedup and grant release, the DAO queries, or the undo flow (DATA-2).

### <a id="test-3"></a>⬜ TEST-3 · Low · No UI or instrumentation tests
- **What:** Only the template `ExampleInstrumentedTest` exists. Espresso is declared but unused.

[↑ Back to index](#index)

---

## I18N / A11Y

### <a id="i18n-1"></a>⬜ I18N-1 · Medium · All UI text is hard-coded
- **Where:** `res/values/strings.xml` holds only `app_name` and `map_id`. There are about 70 literal `android:text` values in layouts, plus Kotlin literals (Toasts, dialogs, status messages, `DateFilterOption` labels, "N Memories" in `Markerable.kt:48`, which is also not pluralized).
- **Fix:** Move the text to `strings.xml` and `plurals`. Store `DateFilterOption` by enum name (already done) and look up labels from resources.

### <a id="i18n-2"></a>⬜ I18N-2 · Low · RTL disabled
- **Where:** `AndroidManifest.xml:29` (`supportsRtl="false"`)

### <a id="a11y-1"></a>⬜ A11Y-1 · Medium · Missing content descriptions
- **What:** Only 6 `contentDescription`s among about 59 image and button views (thumbnails, colour swatches, play/pause, chevrons, pick-media, colour indicators).

### <a id="a11y-2"></a>⬜ A11Y-2 · Low · Colour is the only signal
- **What:** Marker colour, the pie-chart stats and the timeline colour bars carry meaning only through colour. The preset outline uses fixed `Color.BLACK` / `LTGRAY` (`ColorPresetAdapter.kt:42`), which is invisible on a dark theme.
- **Fix:** Add labels or patterns, and use theme attributes for the outlines.

[↑ Back to index](#index)
