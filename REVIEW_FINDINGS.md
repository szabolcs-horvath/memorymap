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
| 9 | ✅ | [ARCH-1](#arch-1) | Hidden tabs have their views destroyed on every tab switch (the map is rebuilt each time). |
| 10 | 🟡 | [DATA-4](#data-4) | Automatic backups are never pruned ~~and the backup list is capped at 100 entries~~ (list paging fixed; pruning postponed). |

---

## <a id="index"></a>Index

✅ 80 fixed · 🟡 7 partly fixed · ❔ 0 to verify · ⬜ 4 open

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

**DATA — Data loss / integrity** · 10 of 12 fixed

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
| ✅ | [DATA-9](#data-9) | Low | Early migrations cannot run below Android 14 |
| 🟡 | [DATA-10](#data-10) | Medium | Too little is stored about each photo to re-link it once its bytes change |
| ✅ | [DATA-11](#data-11) | Medium | Picker grants are kept after re-linking, so the 512 cap pushes out live ones |
| ✅ | [DATA-12](#data-12) | Low | Photos picked and then dropped from the Add form keep their grants |

**BUG — Functional bugs** · 20 of 20 fixed

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
| ✅ | [BUG-17](#bug-17) | Low | Show on Timeline can leave a memory below the screen |
| ✅ | [BUG-18](#bug-18) | Medium | Saving an edit of a deleted memory loses the edit |
| ✅ | [BUG-19](#bug-19) | Low | A save finished on another tab leaves the old title in the Add form |
| ✅ | [BUG-20](#bug-20) | Low | A picked photo edited afterwards can't be re-linked |

**PERF — Performance** · 10 of 10 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [PERF-1](#perf-1) | High | Firebase trace inside the O(N²) clustering loop |
| ✅ | [PERF-2](#perf-2) | High | DAO reflection proxy plus a Firebase trace on every DB call |
| ✅ | [PERF-3](#perf-3) | Medium | Save holds a DB write transaction while reading files |
| ✅ | [PERF-4](#perf-4) | Medium | Media picker work runs on the main thread |
| ✅ | [PERF-5](#perf-5) | Medium | Cold start scans all media twice |
| ✅ | [PERF-6](#perf-6) | Medium | Every small change uploads the whole database |
| ✅ | [PERF-7](#perf-7) | Medium | Map rebuilt on every tab switch or detail open |
| ✅ | [PERF-8](#perf-8) | Low | Full marker reset on every emission |
| ✅ | [PERF-9](#perf-9) | Low | Pin icon recomputed on every render |
| ✅ | [PERF-10](#perf-10) | Low | Global layout listener on every layout pass |

**UX — User experience** · 16 of 19 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [UX-1](#ux-1) | High | Save spinner lasts for the whole Drive upload |
| ✅ | [UX-2](#ux-2) | Medium | Automatic backups block the whole Settings screen |
| ✅ | [UX-3](#ux-3) | Medium | Deleting a Drive backup has no confirmation |
| ✅ | [UX-4](#ux-4) | Medium | Restore requires "Allow all" media access |
| ✅ | [UX-5](#ux-5) | Medium | Location permission asked immediately and repeatedly |
| ✅ | [UX-6](#ux-6) | Medium | Edit mode overwrites an unsaved draft and stays active |
| ✅ | [UX-7](#ux-7) | Medium | Form state lost on process death |
| ✅ | [UX-8](#ux-8) | Medium | Full-screen images are decoded at screen size |
| ✅ | [UX-9](#ux-9) | Medium | Map date filter excludes memories that overlap the range |
| ✅ | [UX-10](#ux-10) | Low | Transitive clustering merges distant points |
| ✅ | [UX-11](#ux-11) | Low | Pickers ignore the locale |
| ✅ | [UX-12](#ux-12) | Low | Media viewer is not immersive |
| ⬜ | [UX-13](#ux-13) | Low | Edge-to-edge enabled and then cancelled out |
| ✅ | [UX-14](#ux-14) | Low | Raw technical text shown to users |
| ✅ | [UX-15](#ux-15) | Low | Placeholder icons and joke version name |
| ✅ | [UX-16](#ux-16) | Low | Confirmation dialogs close when the phone is turned |
| ✅ | [UX-17](#ux-17) | Low | A date picker ignores the picked date after process death |
| ✅ | [UX-18](#ux-18) | Low | "Restore successful" disappears at once |
| ✅ | [UX-19](#ux-19) | Medium | A photo that can't be re-linked can only stay broken |

**ARCH — Structure / maintainability** · 6 of 10 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [ARCH-1](#arch-1) | High | Manual fragment management fights the framework |
| 🟡 | [ARCH-2](#arch-2) | High | Business logic lives in Fragments and the Activity |
| ✅ | [ARCH-3](#arch-3) | High | Background work tied to UI scopes |
| ✅ | [ARCH-4](#arch-4) | Medium | Two Google auth stacks |
| 🟡 | [ARCH-5](#arch-5) | Medium | Duplicated logic |
| 🟡 | [ARCH-6](#arch-6) | Medium | Hand-rolled singletons, no DI |
| ✅ | [ARCH-7](#arch-7) | Low | Stringly-typed data passing |
| ✅ | [ARCH-8](#arch-8) | Low | Shared entity mutated off the main thread |
| ✅ | [ARCH-9](#arch-9) | Low | Dead or misleading code |
| 🟡 | [ARCH-10](#arch-10) | Medium | UI built on XML Views, not Jetpack Compose (every screen is moved; only the optional Compose shell, step 6, is left) |

**BUILD — Build, CI, tooling** · 4 of 4 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [BUILD-1](#build-1) | Medium | CI never runs the tests |
| ✅ | [BUILD-2](#build-2) | Medium | `MAPS_API_KEY` wiring is inconsistent |
| ✅ | [BUILD-3](#build-3) | Low | Supply-chain hygiene |
| ✅ | [BUILD-4](#build-4) | Low | Robolectric cannot run against `targetSdk 37` |

**TEST — Coverage gaps** · 2 of 3 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| 🟡 | [TEST-1](#test-1) | High | No Room migration tests |
| ✅ | [TEST-2](#test-2) | Medium | Critical logic is untested |
| ✅ | [TEST-3](#test-3) | Low | No UI or instrumentation tests |
| ⬜ | [TEST-4](#test-4) | Medium | Flaky UI tests |

**I18N / A11Y** · 2 of 4 fixed

| | ID | Severity | Finding |
|---|---|---|---|
| ✅ | [I18N-1](#i18n-1) | Medium | All UI text is hard-coded |
| ⬜ | [I18N-2](#i18n-2) | Low | RTL disabled |
| ✅ | [A11Y-1](#a11y-1) | Medium | Missing content descriptions |
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
- **Status:** Fixed (decision: exclude). `data_extraction_rules.xml` (cloud backup and device transfer) and `backup_rules.xml` (Android 11 and older) exclude the database and the DataStore. Memories come back only through the app's Drive backups. The DataStore is excluded too, so a new phone doesn't start automatic Drive backups of an empty database before the user restores one. Since `minSdk` 34, only `data_extraction_rules.xml` is needed; `backup_rules.xml` was removed.

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

### <a id="data-9"></a>✅ DATA-9 · Low · Early migrations cannot run below Android 14
- **Where:** `MemoryMapDatabase.kt:54-80`
- **What:** `ALTER TABLE … DROP COLUMN` needs SQLite 3.35+ (API 34+), but `minSdk = 27`. Migrations 1→2 and 4→5 would crash on older devices. This only matters for very old installed databases: backups were introduced at schema version 6 (January 4, 2026), so no backup goes through these migrations.
- **Fix:** Rewrite them as create-new-table, copy, drop, rename, or drop support for schema versions below 5.
- **Status:** Fixed differently. `minSdk` is now 34 (every user has Android 14 or newer), and Android 14 ships SQLite 3.39, so the migrations run unchanged on every supported device.

### <a id="data-10"></a>🟡 DATA-10 · Medium · Too little is stored about each photo to re-link it once its bytes change
- **Where:** `data/MediaItem.kt`, `AddMemoryGroupFragment.readPickedMedia`, `AddMemoryGroupFragmentViewModel.prepareMediaItems`, `util/LocalMediaUtil.kt`
- **What:** A media item stores only its URI, size, date taken and signature (size + hash of the first and last 4 KB). Re-linking needs the exact same bytes, so any edit, re-encoding (Google Photos "Storage saver", a messenger) or metadata rewrite makes the photo unfindable. A missing photo also shows only its type, size and date, so neither the app nor the user can tell what it looked like. Raised by the owner after the main user's logs (2026-11-01): 52 of 1,788 items unlinked; see [UX-19](#ux-19) and [BUG-20](#bug-20).
- **Fix (proposed, not decided):** Store more about each item, but only fields that a matching rule or the [UX-19](#ux-19) chooser uses. Each extra field means a schema change, and a field that nothing reads only adds risk. Most of these can be read from the picked file itself when it is picked (the app already opens it to compute the signature), so they don't need full media access:
  - **Small thumbnail** (about 128 px JPEG, 3–6 KB). This is the most useful field: a missing tile can show what the photo was, and [UX-19](#ux-19) can show it next to the candidates. Cost: about 10 MB for 2,000 items, in the database and in every Drive backup (backups are never pruned, [DATA-4](#data-4)). Decide the size, and whether thumbnails belong in the backup (keeping them out loses them on a new phone, where they help most).
  - **Original file name, width, height, MIME type, video duration.** These come from the picker URI or MediaStore. They are labels and filters for the chooser and a check for [BUG-20](#bug-20), but too weak to link anything on their own (an edit changes the size, a crop changes the dimensions).
  - **Unique ids written into the file:** EXIF `ImageUniqueID`, XMP `DocumentID` / `OriginalDocumentID` (MediaStore also has them as `DOCUMENT_ID` / `ORIGINAL_DOCUMENT_ID`). They are the only fields that could link an edited copy automatically with confidence. First check on a Samsung phone that its camera writes them and that its Gallery keeps them when it edits or copies a photo; if not, don't store them.
  - **Perceptual hash** (64-bit hash of a tiny greyscale copy). It survives re-encoding and resizing but not crops. Use it only to rank candidates in [UX-19](#ux-19), never to link on its own: shots taken seconds apart of the same scene look alike to it, and those are the ones a user keeps and deletes.
  - **Leave out:** GPS (the picker strips it, the app has no `ACCESS_MEDIA_LOCATION`, and per-photo locations add privacy weight for no matching gain); folder, owner app and MediaStore id (they change on a move or copy, and need full access).
- **Things to keep in mind:**
  - Full media access isn't the gate: it adds only the fields left out above. Read everything at pick time for every user.
  - Backfill existing items while their file is still readable: the re-link run already visits every item, so it can fill in missing fields once. Items whose file is already gone (like the 50 in those logs) can't be backfilled.
  - Columns on `media_items`, not a separate table: editing a memory deletes and re-inserts its media rows (new ids), so the values must travel through `SelectedMedia` and `prepareMediaItems` the way the signature does. Follow the schema rules in `data/AGENTS.md`; older backups restore with the new columns empty.
  - A matching rule that uses a new field must be mirrored in `RelinkDiagnostics`.
- **Status:** Partly fixed (2026-11-03). Decided: store the thumbnail (in the database, so in the Drive backups), the perceptual hash, and the name, pixel size, MIME type and video length; measure the unique ids first.
  - **Schema 19.** `media_items` has nullable `displayName`, `width`, `height`, `mimeType`, `durationMs` and `perceptualHash` (a 64-bit dHash). Thumbnails (128 px JPEGs; 856 bytes for a two-colour test photo, a few KB for real ones) are in a new table `media_thumbnails`, keyed by signature. That differs from the proposal above: a column would load every thumbnail with each list of media items. The signature survives the delete-and-reinsert of an edit, and one photo in two memories shares one thumbnail. Restores copy the table (`DatabaseContents`), and `RestoreCompatibilityTest` covers versions 6 to 19.
  - **When the details are read** (`MediaDetails`, through `ContentResolver.loadThumbnail` and the provider's columns): when the Add form saves new picks (media kept through an edit copy theirs from the saved rows, so nothing goes into the saved-state Bundle), when the missing-photo sheet replaces an item, and at start-up for older items whose file is still readable (`MediaDetailsBackfill`). The start-up run also deletes thumbnails no item uses; it is the only place that does, because an Undo of a delete must still find them.
  - **Used:** a missing tile shows the thumbnail dimmed behind its warning, and an item known to be missing shows that way even if an image of it is still in Coil's memory cache. The sheet shows the thumbnail, name and pixel size, and puts suggestions within 10 of 64 hash bits first, marked "Looks alike". `EditedPhotoRule` also requires the same name when both are known.
  - **Diagnostics:** "Send logs" reports how many items have each detail, each unlinked item's stored details, and, for nearby files, the look-alike distance, MediaStore `DOCUMENT_ID` / `ORIGINAL_DOCUMENT_ID` and EXIF `ImageUniqueID`, with a summary of how many carry each id and how many share an original document id.
  - **Tests:** `MediaDetailsTest` (unit: dHash, merging), `NearbyMediaTest` (ranking), `LocalMediaUtilTest` (name rule), `MemoryGroupDaoTest` (thumbnails, guarded fill-in), `RelinkReportTest`, and the UI tests `memory/MediaDetailsTest` (a form-saved photo and a backfilled one show their thumbnail once deleted) and `MissingPhotoTest` (look-alike first, thumbnail and name in the sheet).
  - **Left open:** whether to store and match on the unique ids. Decide from the main user's next "Send logs". Items whose file was already gone before this change can't get details.

### <a id="data-11"></a>✅ DATA-11 · Medium · Picker grants are kept after re-linking, so the 512 cap pushes out live ones
- **Where:** `util/LocalMediaUtil.kt` (`verifyAndFixMediaItems`, the `updateMediaLink` branch)
- **What:** When re-linking moves an item from a photo picker URI to a MediaStore URI, the picker URI's persisted grant is kept. Android keeps at most 512 grants per app and drops the oldest when a new one is taken, so stale grants push out the grants of items that still use picker URIs. The main user's logs (2026-11-01) show 512 grants held, while their two unlinked picker items had none. Those items only work again through re-linking, which needs full media access. It is not yet known how many of the 512 are stale.
- **Fix:** After `updateMediaLink` succeeds, release the old URI's grant if no other item uses it (`releaseUriGrantsIfUnused`). This is not the forbidden "sweep": an older backup that still has the picker URI re-links the same way after a restore, because the photo was just found through MediaStore. Have `RelinkDiagnostics` count the stored URI kinds of all items and how many grants no item uses, to measure the leak first.
- **Status:** Fixed. After a re-link run, the old URIs of the items it moved are passed to `releaseUriGrantsIfUnused`, which releases each grant no item still uses (covered by `UriGrantReleaseTest`). Grants left over from earlier runs are not released (that would be a sweep); they drop out as new picks push them past the cap. The "Send logs" diagnostics now list the stored URI kinds of all items, how many photo picker items still have their grant (and how many distinct picker URIs there are: more than 512 can't all keep theirs), and how many grants no item uses, by kind (`grantUsage`, covered by `RelinkReportTest`). Photos picked in the Add form and then dropped unsaved kept their grants too: [DATA-12](#data-12).

### <a id="data-12"></a>✅ DATA-12 · Low · Photos picked and then dropped from the Add form keep their grants
- **Where:** `fragment/AddMemoryGroupFragment.kt` (the pick launcher, removing a selected photo), `fragment/AddMemoryGroupFragmentViewModel.kt` (`resetForm`, `fillForEditing`)
- **What:** A pick takes a persisted grant of every picked photo or video at once. Only a save released grants (of media an edit removed). A photo removed from the form before saving, a draft cleared or discarded, or a draft replaced by an Edit request kept its grants, which count toward Android's cap of 512 ([DATA-11](#data-11)). Found while fixing DATA-11 (2026-11-03).
- **Status:** Fixed. The form remembers the URIs it picked (`pickedUris`, kept through process death). When one leaves the form (removed, form cleared or replaced by an edit), its grant is released unless a saved media item uses it. While a save runs, releases wait until it ends, so a photo being saved keeps its grant. Media loaded for editing are never released by the form: they may belong to a memory whose delete can still be undone, and a save already releases what an edit removed. Covered by `AddMemoryGroupFragmentViewModelTest` (what the form releases) and `UriGrantReleaseTest` (grants that saved media use stay). Left as is: if the same photo is in a memory deleted while its Undo is still offered, and is picked and then dropped from the form, its grant is released and Undo brings it back without one.

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
- **Status:** Fixed. Each picker is shown under a tag that says what it is for, and its listener is attached again in `onViewCreated` to the picker found by that tag, replacing any earlier listener (`util/PickerListeners.kt`). The Add screen and fragment pickers have distinct tags (they share `childFragmentManager`). A fragment picker carries the fragment's `localId`, so the pick goes to that fragment, or nowhere if it was removed meanwhile. After process death the restored pickers apply to the restored form ([UX-7](#ux-7)); a restored date picker is replaced by a new one, since it would ignore the picked date ([UX-17](#ux-17)).

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

### <a id="bug-17"></a>✅ BUG-17 · Low · Show on Timeline can leave a memory below the screen
- **Where:** `TimelineFragment.performScrollAndFlash`, `res/layout/fragment_timeline.xml`
- **What:** The search bar scrolls away with the list (`scroll|enterAlways`), so while it shows, the bottom of the list sits below the screen by the bar's height. Scrolling by hand hides the bar. Show on Timeline scrolls the list in code and leaves the bar where it is, so a memory near the end of the list (the oldest ones) stays below the screen edge, and so does its flash. Found by the Timeline UI tests (TEST-3); `TimelineTest.showOnTimelineScrollsToTheOldestMemory` is skipped until this is fixed.
- **Fix:** Hide the search bar (`appBar.setExpanded(false)`) when scrolling to a memory, as scrolling by hand does.
- **Status:** Fixed. Show on Timeline hides the search bar (`appBar.setExpanded(false)`) before it scrolls to the memory, as scrolling by hand does. `TimelineTest.showOnTimelineScrollsToTheOldestMemory` runs again. The timeline's screen object now recognises the screen by its list instead of the search bar, which this hides.

### <a id="bug-18"></a>✅ BUG-18 · Medium · Saving an edit of a deleted memory loses the edit
- **Where:** `AddMemoryGroupFragmentViewModel.saveMemoryGroup`, `MainActivity.onMemoryDeleted`
- **What:** An edit with unsaved changes stays open on the Add tab while the user is on another tab. If the memory is deleted meanwhile (from its own screen, and the Undo Snackbar closes), nothing tells the Add tab. Saving then updates a row that no longer exists. Without photos, `updateGroup` changes nothing and the save reports success, so the changes are lost without a word. With photos, inserting them breaks the foreign key, and the user gets "Failed to save: FOREIGN KEY constraint failed (code 787 SQLITE_CONSTRAINT_FOREIGNKEY)". Found by the UI tests (TEST-3); `SaveErrorTest` is skipped until this is fixed.
- **Fix:** In the save, check how many rows `updateGroup` changed (Room's `@Update` can return it). When none, the memory is gone: save the form as a new memory, so the user's work is kept (or ask first: "This memory was deleted. Save it as a new memory?"). Show a plain message instead of the database's text when a save fails for other reasons. `SaveErrorTest` expects the edit to end up in the timeline; adjust it if the fix asks first.
- **Status:** Fixed. `updateGroup` returns how many rows it changed; when none, the memory is gone and the save inserts the form as a new memory, with its photos and fragments, and says so ("This memory was deleted meanwhile, so your changes were saved as a new memory"). The final delete keeps the photo grants of media the Add form still holds (`AddMemoryGroupFragment.mediaUrisInForm`), so the new memory can still show them. Other save errors show a plain message instead of the database's text. `SaveErrorTest` runs again, and also checks that the photo comes through.

### <a id="bug-19"></a>✅ BUG-19 · Low · A save finished on another tab leaves the old title in the Add form
- **Where:** `AddMemoryGroupFragment.onViewCreated`, `res/layout/fragment_add_memory_group.xml`
- **What:** A hidden tab has no view, and Android keeps the state its views had: the title and description text, the All Day switch and the colour sliders. When the tab is shown again, `onViewCreated` fills the form from the ViewModel, and then Android restores that kept state over it. Normally both hold the same, but a save that finishes while another tab is shown clears only the ViewModel. Back on the Add tab, the form had no location but still the old title (and All Day, if it was on), and the title field and the switch wrote them back into the ViewModel, so they went into the next memory. Found by the UI tests (TEST-3), with `AddMemoryTest.aSaveThatFinishesAfterLeavingTheTabSaysSoWithoutOpeningTheMemory`.
- **Fix:** Turn off Android's own state saving on these inputs (`android:saveEnabled="false"`) and fill them from the ViewModel alone, which keeps the form through a rotation and process death.
- **Status:** Fixed. The title, description, All Day switch and colour sliders have `saveEnabled="false"`, and `onViewCreated` sets the switch from the ViewModel too (`updateInputs`). The test above checks that the form is empty and All Day off after such a save.

### <a id="bug-20"></a>✅ BUG-20 · Low · A picked photo edited afterwards can't be re-linked
- **Where:** `util/LocalMediaUtil.kt` (`verifyAndFixMediaItems`), `util/RelinkDiagnostics.kt`
- **What:** Editing a photo in the gallery (a crop, for example) rewrites the file under the same MediaStore id and keeps its date taken, but its size and signature change. An item with a MediaStore URI still opens and shows the edited photo. An item with a photo picker URI breaks once its grant is gone ([DATA-11](#data-11)), and the signature can't find the edited file. Seen in the main user's logs (2026-11-01): item 1913's picker URI ends in `1000021632`, the file with that id has the same name and date taken (to the millisecond), and it was cropped two days after being picked (2.81 MB → 2.48 MB).
- **Fix:** When the signature finds nothing for a local picker URI (`content://media/picker/…/com.android.providers.media.photopicker/media/<id>`), look up `<id>` in MediaStore and link to it if all of these hold:
  - the item was saved by this installation (`deviceId == installationId`); on another phone the id means nothing;
  - the file is of the same type (image or video);
  - its date taken is known (> 0) and equals the stored one to the millisecond. This also guards against Android ever numbering picker ids differently from MediaStore, which is how it behaves but isn't documented.

  Cloud picker URIs (`com.google.android.apps.photos.cloudpicker`) are skipped: their ids aren't MediaStore ids. Count these links separately in the re-link report ("linked to an edited version"), and mirror the rule in `RelinkDiagnostics`.
- **Open question:** after such a link, keep the old signature and size (the current rule: computed once, kept) or replace them with the edited file's? Replacing them lets backups find the photo as it is now, since the old bytes no longer exist on this phone.
- **Status:** Fixed. When the signature finds nothing, `EditedPhotoRule` takes the MediaStore file whose id is in a local picker URI, under the three conditions above (cloud picker URIs are skipped). Decided (2026-11-03): the item then takes the edited file's size and signature (`updateMediaLink` now writes both; a file found by its signature keeps its own), and the old URI's grant is released if no item uses it. The re-link summary counts these links ("… of them to an edited version"), and `RelinkDiagnostics` reports per item what the rule would find. Covered by `LocalMediaUtilTest` (the conditions), `MemoryGroupDaoTest`, `RelinkReportTest`, and the UI test `SettingsTest.reLinkingFindsAPickedPhotoEditedAfterwards` (a picker item whose file was edited in place shows the edited photo after a re-link).
- **Rejected:** matching any file with the same date taken to the millisecond, regardless of id (it would also catch "save as copy" edits). It was rejected as too risky: dates with only whole seconds (videos, messenger images) collide for shots taken in the same second, and two different edits share a date. A wrong link is worse than a missing one; [UX-19](#ux-19) leaves such cases to the user.

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

### <a id="perf-7"></a>✅ PERF-7 · Medium · Map rebuilt on every tab switch or detail open
- **Where:** `MainActivity.kt:99-110, 156-164, 296-302`
- **What:** See ARCH-1. `setMaxLifecycle(CREATED)` destroys the hidden fragments' views, and `replace()` in `onMemoryClicked` removes *all* tab fragments. Each return to the Map recreates `SupportMapFragment`, the GoogleMap, the ClusterManager and all markers.
- **Fix:** Keep tab views alive (`STARTED`, not `CREATED`) or use Navigation with multiple back stacks. Open details with `add` + `hide` instead of `replace`.
- **Plan:** see [ARCH-1](#arch-1), which has the planned fix and what to watch for (stale data).
- **Compose migration ([ARCH-10](#arch-10)):** Compose alone does not fix this, but the migration's last step replaces `MainActivity`'s fragment handling, and that is where keeping the map alive would then be solved. See ARCH-1 for which order to choose.
- **⛔ Precondition:** do not start until the ENTIRE UI is covered by UI tests ([TEST-3](#test-3)): every screen, every user flow and as many edge cases as possible, passing on the local test emulator (docs/ui-tests.md).
- **Status:** Fixed with [ARCH-1](#arch-1): a tab that was shown keeps its view while hidden, and a memory opens over the shown tab instead of replacing every tab, so the map is built once and Back returns to it as it was. A hidden map redrew every marker on each data change until PERF-8 was fixed.

### <a id="perf-8"></a>✅ PERF-8 · Low · Full marker reset on every emission
- **Where:** `MapFragment.kt:503-566`
- **What:** `clearItems()` + `addItems()` + `cluster()` re-renders every marker, even for an unrelated change such as a title edit elsewhere.
- **Fix:** Diff the items, or update only the changed ones (`updateItem`).
- **Status:** Fixed. `MarkerSync` (`util/MarkerChanges.kt`) keeps what the cluster manager holds and gives it only the items that were removed or added since; an edited memory is both. It re-clusters only when something changed or clustering was turned on or off, and an emission with the same items changes nothing. The renderer already kept the marker of an item that came back equal, so the cost was the re-clustering and re-setting every icon. A fragment's marker carries its memory's title as part of its equality (`FragmentMarker`, since ARCH-8), so a renamed memory's fragment counts as changed. Covered by `MarkerChangesTest` and `MapTest.markersFollowARenameAndADeleteMadeOnOtherTabs`.
- **Compose migration ([ARCH-10](#arch-10)):** given up when the Map moved (2026-11-11), as agreed: `maps-compose`'s clustering composable clears and re-adds every item on each change, re-clustering and re-setting every icon again, and `MarkerSync` (`util/MarkerChanges.kt`, with `MarkerChangesTest`) was deleted; it is in the history before that commit. If that makes the map slow with many memories, bring it back and keep the app's own `ClusterManager` with it inside the Compose map instead, created in a `MapEffect` (experimental, `@MapsComposeExperimentalApi`). The cluster manager's `MarkerManager` then takes over the map's marker click listeners from `maps-compose`, which is fine as long as the map has no Compose markers of its own.

### <a id="perf-9"></a>✅ PERF-9 · Low · Pin icon recomputed on every render
- **Where:** `util/MarkerGenerator.kt:59-69`
- **What:** `singleColorPinConfigIcon` runs a 20-step binary search and builds a new `BitmapDescriptor` per marker per render, uncached. The multi-colour cache key uses `colors.hashCode()` (collisions give wrong icons).
- **Fix:** Cache by colour int, and use `colors.joinToString()` as the key.
- **Status:** Fixed. Single-colour pins have a cache of their own (`singleColorPinCache`, by colour, up to 256, since a map can have a colour per memory), which also saves the contrast-colour search; they are small, so they are kept apart from the bitmap icons (`drawnIconCache`, up to 50), and neither pushes out the other. The drawn icons are keyed by every colour (`MarkerGenerator.drawnIconKey`), so two colour lists with the same hash no longer share an icon. Covered by `MarkerGeneratorTest`.

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

### <a id="ux-5"></a>✅ UX-5 · Medium · Location permission asked immediately and repeatedly
- **Where:** `MapFragment.kt:117-123, 455-471`, `PickLocationFragment.kt:164-167, 375-385`
- **What:** The system dialog appears on first launch with no explanation, and again each time the map or picker resumes in a new fragment instance (the "denied" flag lives only in the fragment or ViewModel).
- **Fix:** Ask when the user taps "my location", show a rationale, and persist the denial.
- **Status:** Fixed. Neither the map nor the location picker asks when it opens or resumes any more. Both have their own "my location" button (Google's only appears once access is granted), and `MyLocationAction` asks only when it is tapped: first a short explanation ("Use your location?"), then the system dialog. After a "no", the next tap explains and asks again while Android still allows it; once Android no longer shows its dialog, the button offers to open the app's settings. The denial is kept in DataStore (`LOCATION_PERMISSION_DENIED`), not in the screen, and is cleared once access is allowed. With access, the button moves the map to the user, or selects their location in the picker (a toast says so if the phone's location is off). The decision step is covered by `MyLocationActionTest`.

### <a id="ux-6"></a>✅ UX-6 · Medium · Edit mode overwrites an unsaved draft and stays active
- **Where:** `AddMemoryGroupFragment.kt:522-591`, `MainActivity.kt:382-386`
- **What:** Tapping "Edit" on a memory silently replaces any in-progress new memory. If the user leaves without saving, the "Add" tab still shows the edit form ("Update") next time.
- **Fix:** Ask before discarding a dirty draft, and keep "new" and "edit" separate (for example, edit as its own screen).
- **Status:** Fixed. The form knows whether it has unsaved changes: `hasUnsavedChanges` compares what a save would store with the content when the form was last cleared or loaded for editing (`FormContent`; expanding a section is not a change). An Edit request on a changed form first asks "Discard the new memory?" or "Discard your changes?"; "Keep editing" leaves the form as it is, and the question comes back if the screen is recreated while it is shown. Editing the memory that is already open with changes keeps them. Switching to another tab closes an edit without changes, so the Add tab offers a new memory next time; an edit with changes is kept like a draft, on purpose. Edit as its own screen is left for the navigation rework (ARCH-1). Covered by `AddMemoryGroupFragmentViewModelTest`.

### <a id="ux-7"></a>✅ UX-7 · Medium · Form state lost on process death
- **Where:** `AddMemoryGroupFragmentViewModel.kt:44-69`, `PickLocationFragmentViewModel.kt`, other ViewModels
- **What:** The whole draft (title, media, fragments, dates) lives in plain ViewModel fields, not `SavedStateHandle`. Opening the photo picker or switching apps can kill the process and lose it. The process death UI tests (TEST-3) show what is lost:
  - The Add form keeps only what its text fields show (title, description). The location, dates, colour, photos and fragments go back to a new memory's defaults.
  - An edit forgets which memory it was editing: the button goes back to "Save Memory". Saving needs a location, so nothing is saved at once, but after picking the location again, Save creates a second memory instead of updating the first.
  - The location picker loses the picked spot.
  - `ProcessDeathTest.addFormKeepsTheTitleAndLocation`, `editKeepsEditingTheSameMemory` and `locationPickerKeepsThePickedSpot` are skipped until this is fixed.
- **Fix:** Persist the draft through `SavedStateHandle` or a draft table, including the id of the memory being edited and the location picker's picked spot.
- **Status:** Fixed. The Add form's draft is written to its `SavedStateHandle` when Android saves the app's state (`setSavedStateProvider`) and read back when its ViewModel is created again: every field a save stores (`FormContent`, written as a Bundle in `fragment/FormContent.kt`), the memory being edited, a pending Edit request, which location is being picked, how the form was when last loaded (so unsaved changes still count) and which sections are open. The location picker keeps its picked spot there too. `ProcessDeathTest.addFormKeepsTheTitleAndLocation`, `editKeepsEditingTheSameMemory` and `locationPickerKeepsThePickedSpot` run again, and `FormContentTest` checks that a draft comes back the same.

### <a id="ux-8"></a>✅ UX-8 · Medium · Full-screen images are decoded at screen size
- **Where:** `adapter/MediaPagerAdapter.kt:58-61, 144-149`
- **What:** `size(screenWidth, screenHeight)` limits resolution, so pinch-zoom in PhotoView only upscales a blurry bitmap.
- **Fix:** Load at `Size.ORIGINAL` (or tile with subsampling) when zoomed.
- **Compose migration ([ARCH-10](#arch-10)):** likely made redundant. The migration replaces `MediaPagerAdapter` and PhotoView with a Compose zoom library; telephoto, for example, loads sharper tiles through Coil as you zoom in. A fix in the current code would be thrown away, so do it only if it can't wait.
- **Status:** Fixed with the Compose media viewer (2026-11-09). Photos show through telephoto (`ZoomableAsyncImage` with Coil), which shows the photo at screen size and loads sharper tiles of the file as it zooms; PhotoView and `MediaPagerAdapter` are gone.

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

### <a id="ux-11"></a>✅ UX-11 · Low · Pickers ignore the locale
- **Where:** `AddMemoryGroupFragment.kt:750-751`, `MemoryFragmentEditAdapter.kt:569-570`, `DateFilterOption.kt:15`, `MediaAdapter.kt:78`
- **What:** 24-hour clock forced, "This week" always starts on Monday, and a fixed `yyyy.MM.dd` format in the media error overlay.
- **Fix:** Use `DateFormat.is24HourFormat(context)`, `WeekFields.of(locale)` and localized formatters.
- **Status:** Fixed (2026-11-06).
  - The time pickers show the clock the phone is set to (Android's "Use 24-hour format" switch, or else its language), and so does every time the app shows as text: the Add form and its fragments, the memory screen, the timeline, the map's overlay, the missing-photo sheet and the backup list.
  - Shown dates and times are formatted by Android's ICU library (`DateTimeFormatterUtil`), as the rest of the phone formats them: with the digits of the phone's language (Arabic, Persian, Hindi…) and the calendar chosen for it (Buddhist, Japanese…), which `java.time` ignored. Stored and logged dates (backup file names, `metadata.json`, the database, the logs, the relink diagnostics) keep their fixed formats.
  - "This week" on the map, and the weeks in every date picker, start on the first day of the week of the phone's region, or the one chosen in Android's regional preferences (`util/PhoneCalendar.kt`, through `LocalePreferences`).
  - The media error tiles show the date and time in the phone's own format instead of `yyyy.MM.dd HH:mm`.
  - Covered by `PhoneDateFormatsTest`, `DateFilterOptionTest` and `AddMemoryTest` (both clocks; the UI tests' time picker handles both).
- **Compose migration ([ARCH-10](#arch-10)):** the Material 3 Compose pickers that replace these need the same settings: `is24Hour` from `DateFormat.is24HourFormat` and the first day of the week from `firstDayOfWeek()`.

### <a id="ux-12"></a>✅ UX-12 · Low · Media viewer is not immersive
- **Where:** `MainActivity.kt:304-319`, `res/layout/fragment_media_viewer.xml`
- **What:** The bottom navigation and system bars stay visible, there is no tap-to-hide, and tapping a tab pops the viewer.
- **Fix:** Hide the bottom navigation and system bars while viewing.
- **Compose migration ([ARCH-10](#arch-10)):** heavily affected. The media viewer is rewritten (pager and zoom), and the bottom navigation and the tab-pops-the-viewer behaviour change again if the migration's last step replaces `MainActivity`'s fragment shell. Hiding the system bars (`WindowInsetsControllerCompat`) carries over; tap-to-hide and the bottom navigation are better built into the Compose viewer.
- **Status:** Fixed with the Compose media viewer (2026-11-09). While it shows, the tab bar and the system bars are hidden (`MainActivity.onMediaViewerShown`; a swipe from an edge shows the system bars for a moment), the media sit on black, and tapping shows or hides a top bar with a back arrow and "2 of 6", with a video's Media3 controls. With the tab bar hidden, a tab can no longer be tapped from the viewer. The system bars stay hidden while the top bar shows, so the root's `fitsSystemWindows` (UX-13) leaves no strip around the black.

### <a id="ux-13"></a>⬜ UX-13 · Low · Edge-to-edge enabled and then cancelled out
- **Where:** `MainActivity.kt:84`, `res/layout/activity_main_container.xml:6`
- **What:** `enableEdgeToEdge()` plus `fitsSystemWindows="true"` on the root pads everything, so the map never draws behind the system bars.
- **Fix:** Apply insets per view (map padding, bottom navigation).
- **Compose migration ([ARCH-10](#arch-10)):** heavily affected. Compose handles insets itself (`Scaffold`, `WindowInsets` modifiers, `contentPadding` on the map), so per-view inset code in the XML layouts would be thrown away. Fix it screen by screen during the migration, unless it can't wait.

### <a id="ux-14"></a>✅ UX-14 · Low · Raw technical text shown to users
- **Where:** `SettingsFragmentViewModel.kt:65,84,101` (exception class names), `MapFragment.kt:629` ("Lat: %.4f…"), `MemoryFragment.kt:350`, `MemoryFragmentAdapter.kt:61` (raw coordinates)
- **Fix:** Use friendly messages and formatted coordinates.
- **Status:** Fixed (2026-11-06).
  - A failed backup, restore or delete says what failed and why, in words the user can act on: "Backup failed. There's no internet connection…", "…Google Drive isn't responding…", "…has to be allowed to use Google Drive again: tap Allow access below" (and Settings then offers it), or "Try again. If it keeps failing, send the logs…". The reason comes from `DriveProblem.reason`; a restore the app rejects (too new, unreadable) keeps its own message. The exception itself is only logged, for "Send logs". A file that isn't there no longer counts as a connection problem (`DriveProblem` `OTHER`).
  - Sign-in: a cancelled account picker shows nothing, no account on the phone says so, and anything else asks to check the connection and try again; the details are logged.
  - Coordinates of a place without a name are shown the same way everywhere (the map's overlay, the memory screen, its fragments, the Add form and its fragment cards): "47.4979° N, 19.0402° E", with the phone's decimal separator (`util/Coordinates`).
  - The new text is in `strings.xml`. Covered by `CoordinatesTest`, `DriveProblemTest`, `BackupErrorTest` and `SettingsTest`.

### <a id="ux-15"></a>✅ UX-15 · Low · Placeholder icons and joke version name
- **Where:** `res/menu/bottom_nav_menu.xml` (`@android:drawable/ic_dialog_map` and similar), `app/build.gradle.kts:35` (`versionName = "Debugolunk, debugolunk"`)
- **What:** Legacy framework drawables look out of place in Material 3, and the version name is shown in system settings and the store.
- **Fix:** Use Material Symbols vector icons and semantic versioning.
- **Status:** Fixed (2026-11-06).
  - Every framework drawable (`@android:drawable/…`, `android.R.drawable.…`) is replaced by a Material Symbols Outlined vector (`res/drawable/ic_*.xml`), keeping each control's tint, size and content description. The bottom bar shows the selected tab's icon filled (`nav_*.xml` selectors), as Material 3 does.
  - The version name stays a release nickname on purpose (the owner's choice); `versionCode` is what Android and the Play Store compare.

### <a id="ux-16"></a>✅ UX-16 · Low · Confirmation dialogs close when the phone is turned
- **Where:** `SettingsFragment.kt` (restore, delete backup, delete preset, media access), `MemoryFragment.kt` (delete), `AddMemoryGroupFragment.kt` (clear or discard, replacing an edit), `util/MyLocationAction.kt` (explanation, open settings)
- **What:** These are plain `AlertDialog`s, which Android doesn't bring back after a configuration change: turning the phone closes them, and the user has to start again. The same happens after process death. Found by the rotation and process death UI tests (TEST-3); `RotationTest.restoreConfirmationStaysOpen` and `ProcessDeathTest.restoreConfirmationStaysOpen` are skipped until this is fixed.
- **Fix:** Show them as `DialogFragment`s, with what they need in their arguments or saved state and the answer passed back as a fragment result, so they come back after a rotation or process death.
- **Compose migration ([ARCH-10](#arch-10)):** a Compose dialog is shown from state; kept in the ViewModel or `rememberSaveable`, it survives a rotation, so the migration fixes this if each dialog's state is kept there.
- **Status:** Fixed. Every listed question is now a `util/ConfirmDialog`, a DialogFragment shown in the screen's `childFragmentManager`, so it comes back after a rotation or process death. The answer goes back as a fragment result, listened for in `onCreate`, with what it acts on (the backup, the preset, the memory to edit) in the dialog's arguments. `RotationTest.restoreConfirmationStaysOpen` and `ProcessDeathTest.restoreConfirmationStaysOpen` run again.

### <a id="ux-17"></a>✅ UX-17 · Low · A date picker ignores the picked date after process death
- **Where:** every `MaterialDatePicker`: `AddMemoryGroupFragment.kt` (`pickDate`, `pickDateRange`), `adapter/FragmentEditPickers.kt`, `MapFragment.kt` (custom date filter)
- **What:** If Android kills the app while a date picker is open, the picker comes back when the user returns, and its listener is attached again (BUG-3). But tapping a date and OK applies the date it opened with, not the tapped one. Most likely a Material Components bug: after process death the picker and its calendar each rebuild their own copy of the selection, taps change the calendar's copy, and OK reads the picker's. A rotation keeps one shared copy, so it works there. Time pickers are not affected. Found by `ProcessDeathTest.datePickerStaysOpenAndStillWorks`, which is skipped until this is fixed.
- **Fix:** First check whether a newer Material Components release fixes it. Otherwise, when a date picker comes back after process death (its screen's ViewModel is new), close it and open a new one for the same field with the same selection.
- **Compose migration ([ARCH-10](#arch-10)):** the Compose `DatePicker` keeps its state in `rememberDatePickerState`, which has no second copy, so the migration fixes this.
- **Status:** Fixed in the app: Material Components 1.14.0 is the newest release. When a screen attaches its listener again (`reattachDatePicker` in `util/PickerListeners.kt`), a date picker that lost its state is closed and a new one is shown in its place, built from the same arguments (title, selection, input mode). Whether it lost its state is read from a flag in the picker's own ViewModel, which survives a rotation but not process death, so a picker kept through a rotation stays as it is. `ProcessDeathTest.datePickerStaysOpenAndStillWorks` runs again.

### <a id="ux-18"></a>✅ UX-18 · Low · "Restore successful" disappears at once
- **Where:** `SettingsFragment.kt` (the `RestoreSuccess` branch in `onViewCreated`, `setLoadingState`)
- **What:** After a successful restore, Settings shows "Restore successful" and right away starts re-linking media. The re-link shows the loading overlay with `hideBackupStatus = true`, which hides the result line again, and nothing shows it afterwards. So the user gets no lasting sign that the restore worked. After a backup the line stays: the list reload that follows passes `hideBackupStatus = false`. Found by the Settings UI tests (TEST-3); `SettingsTest.aRestoreSaysThatItSucceeded` is skipped until this is fixed.
- **Fix:** Keep the result line during the re-link after a restore (`hideBackupStatus = false`), or show it again when the re-link is done.
- **Status:** Fixed. The re-link that follows a restore keeps the result line (`hideBackupStatus = false`), so "Restore successful" stays. `SettingsTest.aRestoreSaysThatItSucceeded` runs again.

### <a id="ux-19"></a>✅ UX-19 · Medium · A photo that can't be re-linked can only stay broken
- **Where:** `adapter/MediaAdapter.kt` (the error tile; a tap opens the media viewer), the memory screen, `SettingsFragment` (Re-link)
- **What:** When re-linking can't find a photo's file, the memory shows an error tile forever. The user can't point the app to the right photo, replace it, or remove it without editing the whole memory. The main user's logs (2026-11-01): 52 items unlinked, 50 of them because the file was deleted from the phone. In most of those cases a shot of the same scene taken seconds before or after is still there: the user took two or three shots and deleted the one the memory used. Of the 40 items the diagnostics describe in detail, 18 have a photo of the same type within 1 minute, 20 within 5 minutes, 25 within 10 and 34 within 30.
- **Fix (proposed by the owner):** Tapping a missing tile opens a sheet for that one item:
  1. **Re-link just this item** first: the signature rule and [BUG-20](#bug-20), queued through `MediaRelinker` like every other run. If it finds the file, the tile shows the photo and the sheet says so.
  2. **Otherwise, suggest photos taken around the same time:** same type only, within ±5 minutes of the stored date taken, nearest first, each with its thumbnail, how much earlier or later it was taken, and its size. Leave out photos that are already in this memory (the de-duplication would remove them again). A "Show more" button widens the window once (to ±1 hour?), because nearby shots in the logs are often 10–30 minutes apart. If nothing is in the window, or the date taken is unknown, show no suggestions.
  3. **Actions:** "Use this photo" (replaces the item), "Choose another photo…" (the system photo picker, for any photo), "Remove from memory" (asks first), "Not now".
- **Details to settle:**
  - Listing candidates needs full media access. Without it, the sheet offers "Allow access" and "Choose another photo…".
  - "Use this photo" replaces the item rather than re-linking it: it writes a new URI, signature, size, date taken and device id (unlike `updateMediaLink`), keeps its position in the memory, and releases the old URI's grant if nothing else uses it. A matching DAO method is needed. Since the user chose the photo, there is no false-positive risk, but the sheet should say plainly that it is a different photo ("taken 7 s later").
  - With [DATA-10](#data-10)'s thumbnail, the sheet can show the lost photo next to the candidates. Without it, the user picks from the times alone. The perceptual hash can put the most similar candidate first.
  - Going through the memories one by one to fix 52 tiles is slow, so Settings could also offer "Review unlinked photos (52)", which steps through them with the same sheet.
  - Optionally, include photos in Android's trash (`MATCH_TRASHED`) with a "Restore from trash" action (`MediaStore.createTrashRequest`). It's not clear that Samsung's Gallery uses Android's trash.
  - Build the sheet as a DialogFragment that gets the item id in its arguments, so it survives rotation and process death (as `ConfirmDialog` does). UI tests can add gallery photos at chosen times through `TestData`, and the picker path uses `FakeMediaPicker`.
- **Status:** Fixed (2026-11-03), the core sheet. Tapping a photo or video that needs re-linking opens `MissingMediaSheet` instead of the media viewer. With full media access, it first runs a re-link (`MediaRelinker`); if that finds the file, the sheet closes. Otherwise it suggests up to 20 files of the same type taken within 5 minutes, nearest first, with how much earlier or later and their size (`NearbyMedia`, which leaves out files already in the memory by URI or signature). "Show more" widens to an hour once. Without full access it offers "Allow access". Always offered: "Choose another photo…", "Remove from memory" and "Not now". Using a suggestion asks first. A suggestion or a chosen photo replaces the item (`MediaItemDao.replaceMedia`: new URI, device, type, signature, size and date, same id and place, only while the row is unchanged); removing it closes the `order` gap. Both release the old grant if unused and schedule a backup (`MissingMedia`). Covered by `NearbyMediaTest`, `MemoryGroupDaoTest` and the UI tests in `memory/MissingPhotoTest` (suggestions with Cancel and Use, Show more, no access, another photo, Remove with Cancel, Not now, rotation and process death, a photo the re-link finds).
  - Found along the way: after process death the memory pager dropped its pages' saved state (the adapter didn't know the pages before the memories loaded), so anything open on a memory page was lost. `containsItem` now treats every page as known until the list loads.
  - Not done (later, if wanted): a Settings entry that steps through all unlinked photos, photos in Android's trash, the lost photo's thumbnail ([DATA-10](#data-10)), and ranking by perceptual hash.

[↑ Back to index](#index)

---

## ARCH — Structure / maintainability

### <a id="arch-1"></a>✅ ARCH-1 · High · Manual fragment management fights the framework
- **Where:** `MainActivity.kt` (whole file)
- **What:** Tabs are added up front, then hidden *and* capped at `CREATED`, which destroys their views, so `hide()` is redundant and every switch rebuilds the screen. Details use `replace()`, which tears down all tabs. Cross-screen calls go through `lateinit` fragment references plus `post()`. This causes BUG-6, BUG-8 and PERF-7.
- **Fix:** Navigation component with bottom-nav multiple back stacks (or `STARTED` + `hide` without `replace`). Pass requests as arguments.
- **Plan** (2026-10-25; not started, waiting for UI tests, see below): keep the manual tab management, but stop destroying views. BUG-6 and BUG-8 are fixed already (launchers are property initializers; cross-tab requests wait in the ViewModel), so what is left is PERF-7. The Navigation component would not fix that: its bottom-nav back stacks save each tab's state but still destroy and rebuild its view.
  1. `navigateToTab`: a hidden tab that already has a view is capped at `STARTED` (paused, view kept); a tab never shown stays at `CREATED`, so start-up doesn't build every screen. Loop only over the tabs and the picker, not every fragment in the activity.
  2. `onMemoryClicked`: `add` the pager and hide the current tab at `STARTED`, instead of `replace` (which removes every tab). Back then returns to the tab as it was (camera, open overlay, scroll, search).
  3. `onMediaClick` caps the hidden pager at `STARTED`, and `onPickLocation` the hidden Add form. The picker still goes back to `CREATED` when popped, so a second map doesn't stay in memory.
  4. After the activity is recreated (rotation, process restore), set hidden tabs back to `CREATED` (`commitNow` in `onCreate`), so a rotation doesn't build every visited tab at once.
  5. Cross-tab requests (`requestFocusOnMemory`, `requestScrollToAndFlash`, `requestEditMode`) run in `onResume` (at once if already resumed) instead of as soon as there is a view: a paused tab has a view but is still hidden, so the Timeline would scroll an invisible list and the flash would be lost.
  6. Update AGENTS.md (Navigation, and the "hidden tabs have no view" gotcha).
- **Stale data:** checked for the plan. Map, Timeline and the memory pager draw from Room and DataStore flows collected while `STARTED`, so a paused tab keeps updating (a restore reaches it too), and no write path changes. To handle in the change:
  - Settings reads the "show fragments" and "cluster markers" switches and the default filter once, when its view is built. Nothing else writes them today, but collect them instead, or they go stale once the view outlives a tab switch.
  - Add tab: a save error would be shown (toast) while another tab is shown, instead of when the Add tab is opened next. `isScreenShown` already keeps a success from opening the memory.
  - The keyboard may stay open after switching tabs from the Timeline search (the view is hidden, not destroyed).
  - Hidden tabs keep re-drawing on every data change; for the map that is a full marker reset (PERF-8).
- **⛔ Tests first (hard precondition):** this changes the lifecycle of every screen, so **it must not be started until the ENTIRE UI is covered by UI tests (TEST-3): every screen, every user flow and as many edge cases as possible, passing on the local test emulator (docs/ui-tests.md).** Tests for only the flows listed here are not enough. These flows matter most and must be among them: switching tabs; Show on map, Show in timeline and Edit from a memory, also while the target tab already has a view; the location picker round trip; back from a memory and from the media viewer; rotation and process death; and data changed on one tab showing on another. After the change, check on a device that the map is built only once (`dumpsys activity`) and compare tab-switch times (the `main_activity_navigate_to_tab_*` traces).
- **Compose migration ([ARCH-10](#arch-10)):** both wait for the same complete UI test suite (TEST-3). The migration keeps the fragments for most of its steps, so this plan still applies alongside it. Only its last, optional step replaces `MainActivity`'s fragment handling with a Compose shell, which would throw this work away and solve PERF-7 there instead. Once TEST-3 is done, decide: if the Compose shell is planned soon, skip this plan and solve PERF-7 in that step; otherwise do this plan first.
- **Status:** Fixed (2026-11-04), as planned, keeping the manual tab management; the Compose shell isn't planned soon. `navigateToTab` caps a hidden tab that has a view at `STARTED` and one never shown at `CREATED`, and loops only over the tabs and the picker. `onMemoryClicked` adds the memory over the shown screen and hides it at `STARTED`; the media viewer and the location picker hide the memory and the form at `STARTED` too, and the picker goes back to `CREATED` when it closes. After a recreation, `releaseHiddenViews` puts every hidden screen back to `CREATED`. Show on Map and Show in Timeline run in `onResume` (at once if already resumed); Edit still runs as soon as the form has a view, since filling a hidden form shows nothing. The stale-data points: the Settings switches and default filter now follow DataStore; the Add tab's save result is handled only while the tab is shown (a save error waits for it, and a save that finished elsewhere shows the cleared form, or an edit opened meanwhile); the keyboard is closed whenever a screen is hidden. Hidden tabs still update on data changes; the map now changes only the markers that changed (PERF-8). New UI test: `DetailNavigationTest.theKeyboardClosesAndTheSearchStaysWhenAMemoryOrAnotherTabIsShown`.

### <a id="arch-2"></a>🟡 ARCH-2 · High · Business logic lives in Fragments and the Activity
- **Where:** `SettingsFragment.kt` (1041 lines), `AddMemoryGroupFragment.kt` (786), `MapFragment.kt` (778), `MainActivity.kt`
- **What:** DAO writes (preset CRUD, delete, undo transaction), backup triggers, relink orchestration and permission flows are written inline in UI classes. `CommonViewModel` hands raw `getDb()` and `getMemoryGroupDao()` to the UI. ViewModels expose mutable `var`s and a `MutableList` shared directly with an adapter (`viewModel.fragments` → `MemoryFragmentEditAdapter`).
- **Fix:** Add repository and use-case classes (`MemoryRepository.save/delete/restore`, `BackupScheduler`, `MediaRelinker`). ViewModels expose immutable `StateFlow<UiState>` and take intents.
- **Compose migration ([ARCH-10](#arch-10)):** the ViewModel part (`StateFlow<UiState>` plus intents) is step 2 of the migration, done one screen at a time right before that screen moves to Compose. Doing it earlier is fine and makes the migration smaller. The repository and use-case part does not depend on the migration.
- **Status:** Partly fixed (2026-11-08): the repository part.
  - No screen touches the database or a DAO any more. `MemoryRepository` (save, reorder, delete, undo, the final delete's grant release, thumbnails) and `PresetRepository` (add, save, delete, reorder, the first-start defaults) do the writes, and each schedules the automatic backup itself. `CommonViewModel` no longer hands out `getDb()` or DAOs, and `CommonRepository` is gone.
  - Moved out of UI classes: the Add form's save transaction (from its ViewModel), the delete and the thumbnail lookup (`MemoryFragment`), the undo and the final delete (`MainActivity`), the preset writes, the backup list and the missing-media check (`SettingsFragment`), the default presets (`MainActivity`), and the missing-photo sheet's reads.
  - The ViewModels that use data take their repository in the constructor, through a `Factory` ([ARCH-6](#arch-6)). `SaveMediaTest` builds the Add form's with an in-memory database.
  - The backup triggers already had their own classes (`AutomaticBackups`, [ARCH-3](#arch-3)), as did re-linking (`MediaRelinker`, `RelinkAction`) and the location permission flow (`MyLocationAction`).
  - Left for the Compose migration, one screen at a time (step 2 of [ARCH-10](#arch-10)): immutable `StateFlow<UiState>` plus intents instead of the ViewModels' `var`s, and the `MutableList` the Add form's ViewModel shares with `MemoryFragmentEditAdapter`.
  - Done for the Timeline (2026-11-08): `TimelineFragmentViewModel` exposes one `StateFlow<TimelineUiState>` (the search, the memories grouped by day, a pending Show on Timeline) and takes intents (`onQueryChange`, `requestScrollTo`, `onScrollHandled`).
  - Done for Settings (2026-11-08): `SettingsFragmentViewModel` exposes one `StateFlow<SettingsUiState>` and takes intents. The map preferences (`MapPreferencesModel`) and the colour presets (`ColorPresetsModel`) are parts of their own, and the backup list, results and busy state that `SettingsFragment` used to keep in its views are in the state. The fragment keeps only what needs an activity: sign-in, Drive's consent screen, permission requests, the questions asked first, and sharing the logs.
  - Done for the Add/Edit form (2026-11-10): `AddMemoryGroupFragmentViewModel` publishes one `StateFlow<AddFormUiState>` after every change and takes intents (`changeTitle`, `setDate`, `setColor`, `addFragment`, `requestSave` and the rest). Its fields stay, behind those intents, for the unsaved-changes check and the saved draft; the adapter that shared its fragment list is gone.
  - Done for the location picker and the Map (2026-11-10 and 11-11): `PickLocationFragmentViewModel` publishes `PickLocationUiState` and `MapFragmentViewModel` `MapUiState` (the pins, the filter, the colour counts, the panel's pin and the camera's next move), both taking intents. `MapFragment` still decides when the camera moves and which pin a "show on map" request selects.

### <a id="arch-3"></a>✅ ARCH-3 · High · Background work tied to UI scopes
- **Where:** All `triggerAutomaticBackup` callers; relink in `MainActivity` / `MemoryFragment` / `SettingsFragment`
- **What:** Backups and relinks run in `lifecycleScope` or `viewModelScope`. They are cancelled or lost when the screen goes away or the process dies, and nothing coordinates or deduplicates them (DATA-3, PERF-6).
- **Fix:** WorkManager unique work (`enqueueUniqueWork("auto-backup", REPLACE)` with a short initial delay and a network constraint) and a single `MediaRelinker` with a mutex.
- **Status:** Fixed. Automatic backups run in WorkManager (`AutomaticBackupWorker`), so they survive the screen and the process and are deduplicated. Media re-linking goes through `MediaRelinker.relink`: runs happen one at a time (a `Mutex`) in `MemoryMapApplication.applicationScope`, so they finish when the screen goes away, and requests made during a run share the next run (`SerialRunner`, covered by `SerialRunnerTest`). Screens only wait for the result and show its progress. A run killed with the process is not resumed; the next cold start runs it again.

### <a id="arch-4"></a>✅ ARCH-4 · Medium · Two Google auth stacks
- **Where:** `auth/GoogleAccountService.kt`, `SettingsFragment.kt` (`requestDriveAuthorization`), `backup/DriveBackupStorage.kt`
- **What:** Credential Manager is used for sign-in and `AuthorizationClient` for consent, but Drive calls use the legacy `GoogleAccountCredential.usingOAuth2` + account name (AccountManager-based). The access token from `AuthorizationResult` is thrown away. `UserRecoverableAuthIOException` from `listBackups` is swallowed as "no backups".
- **Fix:** Use `AuthorizationClient` for tokens everywhere (silent `authorize()` for automatic backups) and surface recoverable auth errors.
- **Status:** Fixed (2026-11-06).
  - Recoverable auth errors are surfaced: `listBackups` returned an empty list on any `IOException`: lapsed consent, but also no connection or a Drive outage, and a failed refresh wiped a list already shown. To the user it looked as if the backups were gone. It now returns a `Result`, and `DriveProblem` sorts the failure (needs access, no connection, Drive unavailable, other). Settings keeps the list it showed and says why under "Available Backups"; when access lapsed it offers "Allow access", then reloads the list. Automatic backups decide what to retry with the same `DriveProblem`. Covered by `DriveProblemTest` and `BackupListProblemTest` (with the fake Drive failing).
  - One auth stack (2026-11-06): Drive calls send the access token `AuthorizationClient` gives for the signed-in account (`GoogleAccountService.driveAccessToken`; the consent request names that account too), instead of `GoogleAccountCredential` and the account name. When access has to be allowed first, the token request throws `DriveAccessNeededException` with the consent screen, which "Allow access" opens through the app's consent launcher; a background backup can't show it, so it stops retrying. A token Drive rejects (401) is cleared (`AuthorizationClient.clearToken`) and replaced once; rejected again, access is needed. `google-api-client-android` is no longer a dependency. Covered by `DriveBackupStorageTest` (the token retry), `DriveProblemTest` and `BackupListProblemTest`, and checked by hand with a real account on the test emulator: sign-in, the backup list, Backup now, an automatic backup, restore, delete, and access removed in the Google account (the list says so, and "Allow access" brings it back).

### <a id="arch-5"></a>🟡 ARCH-5 · Medium · Duplicated logic
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
- **Compose migration ([ARCH-10](#arch-10)):** the UI items (the HSV slider, preset and animation UI ×3, the date and time pickers ×2, `showError` ×2) become shared composables during the migration. A `ColorPickerView` or `DateRangeEditor` built as a View now would be thrown away, so build those as composables then. The non-UI items (comparators, `getFormattedDate`, `hsvToColor`, the media metadata query, the relink trigger logic) don't depend on the migration and can be done any time.
- **Status:** Partly fixed (2026-11-07). The non-UI items each have one shared version:
  - Comparators: `MediaItem.DISPLAY_ORDER` and `MemoryFragment.DISPLAY_ORDER` (data), used by the memory screen, the media viewer and the Add form (`DisplayOrderTest`).
  - `getFormattedDate`: one implementation in `Markerable`, through `DateTimeFormatterUtil.dateRange` (`PhoneDateFormatsTest`).
  - Default colours: `Markerable.markerColor()` replaces the repeated `hsvToColor(h ?: DEFAULT…)` calls on the map, timeline, overlay and memory screen (`ColorUtilTest`).
  - Media metadata query: the Add form's pick and save use `LocalMediaUtil.queryMetadata`, which re-linking already used: three copies, not two.
  - Relink trigger: the memory screen's and Settings' Re-link buttons share `util/RelinkAction` (run with full access, or ask for it first and run with what was granted). Each screen keeps its own progress display, which is UI. The start-up re-link in `MainActivity` does something else (it offers access in a Snackbar only when there is none) and stays as it is.
  - Location permission handling was already shared (`util/MyLocationAction`, used by the map and the location picker).
  - The HSV slider and presets (2026-11-10): one set of composables in `ui/color/ColorControls.kt` (`Swatch`, `ColorSliders`), used by Settings, the Add form and each of its fragments.
  - The date and time pickers (2026-11-10): one `DateRows` and one `FormPicker` (`ui/add/FormDates.kt`) for the memory and its fragments; the old fragment editor's picker code is gone.
  - Left: a missing photo's details (type, size, date) are drawn by two composables, the memory page's `MissingTile` and the Add form's `MediaDetails`.

### <a id="arch-6"></a>🟡 ARCH-6 · Medium · Hand-rolled singletons, no DI
- **Where:** `CommonRepository`, `MemoryMapDatabase`, `InstallationIdentifier`, `BackupManager(context)` created per fragment
- **What:** `MemoryMapDatabase.getDatabase` has no second null check inside `synchronized`, so two threads can build two databases (`MemoryMapDatabase.kt:94-107`). The DAO cache fields are not volatile.
- **Fix:** Hilt or Koin (or at least a small `AppContainer` in `Application`), and fix the double-checked locking.
- **Status:** Partly fixed: `MemoryMapDatabase.getDatabase` now re-checks inside `synchronized`. `AppServices` holds the outside services behind interfaces (`accounts` for sign-in and Drive consent, `backupStorage` for Drive files), which UI tests replace with fakes. `AppContainer` (2026-11-08, in `MemoryMapApplication`) builds the database, `MemoryRepository`, `PresetRepository` and one `BackupManager`, and the ViewModels that need them get them through their constructor (a `Factory`), so `CommonRepository`'s singleton and the per-fragment `BackupManager` are gone. Left: no DI framework (a small container was enough so far), and code outside the UI (re-linking, `MissingMedia`, the details backfill, the backup worker, the relink diagnostics) still reaches the database through `MemoryMapDatabase.getDatabase` instead of the container.

### <a id="arch-7"></a>✅ ARCH-7 · Low · Stringly-typed data passing
- **Where:** `MemoryFragmentListener.onMediaClick(ArrayList<Pair<String,String>>)`, `MediaPagerAdapter` (`item.second == "VIDEO"`), `MediaViewerFragment` (Serializable `Pair` list in arguments, which also risks `TransactionTooLargeException` for large memories)
- **Fix:** Pass the memory id + start index and load the media in the viewer's ViewModel.
- **Status:** Fixed (2026-11-06). The viewer's arguments hold only the memory's id and the id of the item it opened on (`MediaViewerFragment.newInstance(memoryId, mediaItemId)`), so they stay small however many photos the memory has. `MediaViewerViewModel` loads the memory's media from the database in the memory screen's order (`MediaItem.DISPLAY_ORDER`, now shared), follows their changes, and keeps the item on screen by id in its saved state, so rotation and process death come back on the same photo. `MediaPagerAdapter` takes `MediaItem`s and checks `MediaType.VIDEO`. Covered by `MediaViewerTest.theViewerStaysOnTheSwipedToPhotoThroughRotationAndProcessDeath`.

### <a id="arch-8"></a>✅ ARCH-8 · Low · Shared entity mutated off the main thread
- **Where:** `MapFragmentViewModel.kt:89`, `data/MemoryFragment.kt:47-48`
- **What:** `fragment.title = parent.title` mutates Room-emitted objects on `Dispatchers.Default`, and those objects are shared with other collectors. `title` is a `@Ignore var` that is not part of `equals`.
- **Fix:** Map to an immutable UI model (`MarkerItem(title, …)`).
- **Status:** Fixed. The map wraps each fragment in `Markerable.FragmentMarker(fragment, title)` instead of setting the title on the object Room emitted, and `MemoryFragment.title` is no longer a mutable field (a fragment has no title of its own). The title is part of the wrapper's equality, so the map's marker diff compares items directly. Covered by `MarkerChangesTest` and `MapTest.markersFollowARenameAndADeleteMadeOnOtherTabs`.

### <a id="arch-9"></a>✅ ARCH-9 · Low · Dead or misleading code
- `AddMemoryGroupFragmentViewModel.isInitialized` (written, never read)
- The `IS_FIRST_RUN` / `LAST_APP_VERSION` block in `MainActivity.kt:244-256` writes the version twice and reads it nowhere
- The commented-out passkey and password branches in `GoogleAuthManager.kt`
- `ExampleUnitTest` (`ExampleInstrumentedTest` is gone)
- The DataStore is named `"googleAuthDatastore"` but holds all app preferences
- The default literals in `SettingsFragment.kt:290,313` duplicate the `PreferencesKeys` defaults
- `SIGNATURE_FIX_PLAN.md` now documents media re-linking, not a "fix plan"; move it to `docs/media-relinking.md`
- `ColorPresetAdapter.setSelectedPresetId` is never called from the Add screen, so the chosen preset is never highlighted there
- **Status:** Fixed (2026-11-04).
  - Removed: the Add form's `isInitialized` (Settings' is read, so it stays), the second write of `LAST_APP_VERSION`, and `ExampleUnitTest`. `GoogleAuthManager.kt` was already gone, with its commented-out branches.
  - Kept on purpose: `LAST_APP_VERSION` itself (nothing reads it yet, but it is the only record of the version an installation last ran, for a step that must run once after an update), and the DataStore's name (renaming it would lose every user's preferences without a migration; AGENTS.md explains it).
  - Settings' switches use the `PreferencesKeys` defaults.
  - `SIGNATURE_FIX_PLAN.md` became `docs/media-relinking.md`, trimmed to the evidence, the reasons behind the rules and the known limits; the rules themselves are in AGENTS.md.
  - The Add form highlights the preset that matches the memory's colour, and each fragment's picker its own (`ColorPresetAdapter.setSelectedColor`, `HSVPreset.hasColor`), so the highlight follows a preset tap, a slider and a memory loaded for editing. Covered by `FragmentsAndColorTest.thePresetOfTheFormsColorIsHighlightedUntilTheColorChanges`.

### <a id="arch-10"></a>🟡 ARCH-10 · Medium · UI built on XML Views, not Jetpack Compose
- **Where:** all of `fragment/`, `adapter/`, `MainActivity.kt`, `view/MaxHeightCardView.kt` and `res/layout/`. As of `28df90b` that is about 6,000 lines of Kotlin and 2,900 lines of layout XML.
- **What:** The UI uses XML layouts, ViewBinding, fragments and RecyclerView adapters. Views now mostly get maintenance, while Google builds new UI features for Compose first. Several rules in AGENTS.md exist only because of the View system: `_binding ?: return` and `viewLifecycleOwnerLiveData`, launchers as property initializers, the Slider listener trap, the picker tags and `setOnPicked` after rotation, and the `DiffUtil` adapters. This is about upkeep, not a bug users see.
- **⛔ HARD PRECONDITION: do not start any part of this migration, not even a pilot screen, until the ENTIRE UI is covered by UI tests (TEST-3).** That means every screen, every user flow, and as many edge cases as possible, all passing on the current View UI. Partial coverage, "the main flows" or "the screen being migrated" is NOT enough. Until TEST-3 is done this item stays blocked, whatever the order of other work. The tests must also run unchanged against each migrated screen (see TEST-3 for how), because they are the only proof that behaviour stayed the same.
- **Same behaviour; the look follows Material 3 Expressive:** a migrated screen must behave as before (the UI tests prove it). Its look does not have to match the old View version pixel for pixel: where the Compose version is better looking or more idiomatic (Material 3 Expressive as [m3.material.io](https://m3.material.io/) describes it, Compose's own defaults, Android conventions), keep it, and don't spend time matching small differences such as a few pixels of text height or spacing. Screenshots of both versions are compared to catch regressions (something missing, a wrong colour, a broken layout), not offsets. Before a screen moves, review it against Material 3 Expressive (plan step 3), and let that review, not the old screen, decide its look. A change in behaviour is allowed only when it is objectively better: fixing a bug, an open finding (such as UX-8 or UX-13), an accessibility problem, or a mismatch with Material 3 guidance; "easier to build in Compose" does not count. Each change in behaviour, and each visible change from the review, must be:
  - deliberate, never a side effect noticed afterwards
  - named, with the reason, in the commit message
  - covered by updating the affected UI tests in the same commit, so the tests describe the new behaviour; never edit a test only to make it pass.
- **Assessment** (2026-10-25): technically feasible and worth doing, but only one screen at a time, never as a big-bang rewrite. The data, backup, re-linking, hashing and WorkManager code (about 4,500 lines) is not touched. Every UI part has a Compose equivalent:
  - Map: `maps-compose`. Keep the existing `ClusterManager`, `MarkerableClusterRenderer` and `MarkerGenerator`, and attach them through the map's effect API instead of using Compose-drawn cluster content (slower with many markers). This is the hardest screen.
  - `ViewPager2` (memory pager, media) → `HorizontalPager`. PhotoView → a zoom library (for example telephoto).
  - Pie chart library → a small Canvas `drawArc`, which removes a dependency.
  - `ItemTouchHelper` drag-to-reorder (memory fragments, media, colour presets) → not built in, needs a library (for example `sh.calvin.reorderable`).
  - Material date and time pickers → Material 3 `DatePicker` / `TimePicker`, which removes the rotation workaround in `util/PickerListeners.kt`.
  - `ListAdapter` / `DiffUtil` → `LazyColumn` with stable keys. Places autocomplete does not depend on Views and stays.
  - Icons: the Material Symbols vector drawables (`res/drawable/ic_*.xml`) work in Compose as they are, through `painterResource(R.drawable.ic_map)`. Another option is pulling in Compose's Material icons library (`androidx.compose.material:material-icons-extended`, used as `Icons.Outlined.Map`), which needs no files of our own. Decide when the migration starts: that library has the older Material Icons, not Material Symbols, so some icons look slightly different. Also check that it is still updated, and keep R8 on so its thousands of unused icons don't end up in the APK.
- **Caveats:**
  - Compose does not fix PERF-7 by itself. A `NavHost` also throws away screens that aren't shown, so keeping the map alive is still a deliberate decision (see ARCH-1).
  - Behaviour built up over many commits has to be carried over (see the rule above): unsaved changes and edit mode on the Add tab, pending cross-tab requests, map padding around the overlay and the Google logo, the undo Snackbar and its grant release, and asking for location only from a "my location" button.
  - The APK gets a few MB bigger, and the Firebase Perf traces for tab switching (`main_activity_navigate_to_tab_*`) need new names.
- **Related findings** (each has a "Compose migration" note):
  - Likely made redundant, or fixed differently, by the migration: [UX-8](#ux-8) (zoomed images), [UX-13](#ux-13) (insets), [UX-12](#ux-12) (immersive viewer), the UI parts of [ARCH-5](#arch-5) (shared colour and date components), and the preset outline in [A11Y-2](#a11y-2).
  - Part of the migration, or better done before it: [ARCH-2](#arch-2) (step 2), [A11Y-1](#a11y-1) (the UI tests rely on content descriptions), [I18N-1](#i18n-1) (the resources carry over).
  - Same test gate, and the shell question in step 6: [ARCH-1](#arch-1), [PERF-7](#perf-7).
- **Plan** (in progress since 2026-11-08, the precondition met: see Status):
  1. Gate check: confirm TEST-3 is complete and the whole suite passes on the local test emulator (docs/ui-tests.md), as listed there. If anything is missing, stop and finish the tests first.
  2. Per screen: first move its ViewModel to an immutable `StateFlow<UiState>` plus intents (ARCH-2). Then replace the fragment's layout with a `ComposeView`, keeping the fragment and `MainActivity`'s tab handling. This works with or without the ARCH-1 change.
  3. Right before each screen moves: review it against Material 3 Expressive. [m3.material.io](https://m3.material.io/) is the source of truth (the component's specs and guidelines: layout, colour roles, shapes, type scale, states, accessibility), together with idiomatic Android and Compose conventions. List what doesn't conform, with the M3 page for each, decide with the user which to adopt, and build the Compose version that way. The Timeline was moved before this step existed: review it too, before Settings.
  4. Screens, easiest first: Timeline (pilot), Settings, memory detail with pager and media viewer, Add/Edit, then the location picker and the Map.
  5. After each screen: run the full UI test suite (not only that screen's tests), the unit tests, `detekt` and `lintDebug`, and check on a device. Compare screenshots of the screen before and after, in light and dark theme, in portrait and landscape, for regressions (see the rule above). Delete the old layout, adapter and any dependency that is no longer used, and remove the View-only rules from AGENTS.md. One screen per commit.
  6. Last, and only if it is worth it: replace the fragment shell with a Compose scaffold and decide then how hidden tabs stay alive (ARCH-1, PERF-7).
- **Status:** In progress (2026-11-08).
  - Gate check passed: the whole suite (116 tests) passed on master's View UI on the test emulator, none without a result.
  - Timeline moved (pilot): `ui/timeline/TimelineContent.kt` in `TimelineFragment`'s `ComposeView`, its ViewModel on `StateFlow<TimelineUiState>` ([ARCH-2](#arch-2)). `TimelineAdapter` and the three Timeline layouts are gone. Compose came in with the BOM, Material 3 and `lifecycle-runtime-compose`; `ui/theme/MemoryMapTheme` takes the colours and corner sizes from the XML theme and uses Compose's Material 3 type scale, for the screens that follow (AGENTS.md, "Compose").
  - Checked: the whole suite passes against it (116 tests), unchanged apart from `TimelineScreen` finding the search field by its tag. Screenshots and element bounds of both versions were compared in light and dark theme, portrait and landscape, scrolled, searching, empty, pressed and with the Scroll to top button: the same layout, colours, corners, flash, lifted search bar and keyboard behaviour; text follows Compose's type scale, a few pixels taller per line than a `TextView`.
  - Deliberate changes: the search field's placeholder uses Material 3's placeholder colour (`onSurfaceVariant`) instead of the old 38 % hint colour, about 2.3:1 contrast on the field, below the 4.5:1 text needs; and the field is Material 3's 56 dp high instead of 53.7 dp. Kept as found: the list's end sits below the screen while the search bar shows, as with the View version's `CoordinatorLayout`.
  - Found on the way: `MainActivity` had no `windowSoftInputMode`, so the keyboard resized the window only because the visible screen had a View scroll container; it is now `adjustResize` explicitly.
  - Timeline reviewed against Material 3 Expressive (step 3), each change looked at on the emulator before it was kept: Material 3's `TopSearchBar` (the list scrolls beneath it), a segmented list of filled items with day labels instead of dividers, and a tonal Scroll to top button plus tapping the tab again. Tried and dropped: a pin icon instead of the colour strip (blander). Not adopted: the focused search bar's narrower margins, which `TopSearchBar` in Material3 1.4 shows only in its expanded search view.
  - Settings reviewed against Material 3 Expressive and moved (`ui/settings/`), with every change the review proposed, agreed with the user first: the page has sections (Map, Backup & restore, Media library) under a large flexible top app bar, where each setting is a standalone item and only real lists (the backups) are segmented; the default map filter is chosen from Material 3 Expressive's menu, opening from its arrow; the backups are rows of the page instead of a fixed 300 dp box with its own scroll (the newest five, with "Show all backups" opening an All backups page in the tab, which Back closes), and pulling the page down refreshes them; Disconnect is a text button on the Google Drive account row instead of a filled red button; labels are in sentence case ("Back up now"); the result line is a tonal banner with an icon; the colour editor opens inside the presets' card, its sliders have no tick dots or stop indicator, the swatches a ring and a check mark when selected, and Delete, Undo and Save form a button group; the busy card has a wavy progress bar. `BackupAdapter`, the Settings layouts and SwipeRefreshLayout are gone; `ColorPresetAdapter` stays for the Add form.
  - Material3 is pinned to 1.5.0-rc01, ahead of the BOM's 1.4.0: the stable BOM takes only stable releases, and button groups, wavy progress indicators and `SegmentedListItem` came in 1.5. 1.5 also renamed `SearchBarScrollBehavior.scrollOffset` to `scrollState.scrollOffset` (the Timeline's scroll to top), and its `TopSearchBar` no longer shows the keyboard for typing in the collapsed bar: the field is meant to open the expanded search. So the Timeline now searches the Material 3 way, agreed with the user: tapping the bar opens `ExpandedFullScreenSearchBar` (full screen, over the tab bar, with a back arrow, a clear button and the matching memories as results); Back or the arrow closes it and the query stays, with the Timeline filtered. Tapping a result closes the search view first, so Back from the memory returns to the Timeline.
  - Left for later, agreed with the user:
    - Done (2026-11-11, at the start of step 6): upgrade `activity-compose` from 1.8.2 (the version other libraries brought in, declared for Settings' `BackHandler`) to the latest stable. It requires the same version of `activity` itself, so the whole app's Back handling, result launchers (photo picker, permissions, Drive consent, sign-in) and edge-to-edge move with it, and from 1.12 Back runs on `navigationevent` (predictive back). Do it on its own and run the full UI suite.
    - When the Map moves: the date filter's options are in title case ("Last 7 Days", "All Time") while the moved screens use sentence case. The map's filter bar and the Settings menu share these strings.
    - Move Material3 back to the BOM's version once the BOM has 1.5 (see the pin above).
  - Memory detail screen reviewed against Material 3 Expressive; agreed with the user (2026-11-09):
    - Memory page: a large flexible top app bar with the title, a back arrow, Edit, and Delete in a More menu; "Show on map" and "Show on timeline" as tonal buttons under the date and place; the fragments always shown, as a segmented list with each one's colour strip (tap one to show it on the map); the marker colour as a strip on the header instead of a coloured divider.
    - Media: the missing-media warning as a tonal banner in theme colours (it was fixed orange, even in dark mode); the grid with rounded tiles and small gaps, the missing tile in theme colours and still with the stored low-resolution thumbnail when there is one.
    - Media viewer: immersive (black, tab bar and system bars hidden, tapping shows or hides a top bar with a back arrow and "2 of 6"; UX-12); zoom with telephoto, which loads sharper tiles as you zoom (UX-8); videos played by Media3 (ExoPlayer) with its own Material 3 controls (`media3-ui-compose-material3`), shown and hidden with the top bar.
    - Missing-photo sheet: Material 3's drag handle; "Choose another photo" and "Remove from memory" as list items with icons; no "Not now" button (dragging the sheet away, tapping outside or Back closes it).
  - Memory detail screen moved (2026-11-10): each page of the memory pager (`ui/memory/MemoryContent.kt`, `MemoryMedia.kt`; the pager stays a `ViewPager2` of fragments), the missing-photo sheet (`ui/memory/MissingMediaSheetContent.kt`, still a `BottomSheetDialogFragment`) and the media viewer (`ui/media/`). The four adapters, seven layouts and PhotoView (with the JitPack repository it needed) are gone; the drag-to-reorder of photos and fragments uses `sh.calvin.reorderable`, zoom telephoto and videos Media3.
    - Found on the way: the emulator's hardware video decoder fails when an app runs it (the old `VideoView` decoded in the media server), so the UI tests play videos with software decoders (`AppServices.videoDecoders`); the app keeps the phone's own choice. Media3's position slider has no accessible name of its own (Material3 1.5's slider keeps its node apart from any name, as in Settings), so a box around it gives it its name, position and length. The viewer turns off the root layout's `fitsSystemWindows` while it shows, so it reaches the screen's edges. Android's "Viewing full screen" notice, shown the first time an app hides the bars, is marked as seen by `MemoryMapAppRule`.
    - After the user's look: the fragments' colour runs down each row's left edge, as on the details card, and the rows are standard one-line list items (a 40 dp colour pill had made them taller). The media viewer always uses the dark colour scheme (`MemoryMapTheme(dark = true)`), on a black `Surface` that gives its content a light colour: Media3 takes its controls' colours from the theme, so in light mode the slider and the times had shown light-theme colours on the black viewer. The position slider is built as Media3's `ProgressSlider` builds it (Media3's progress state with Material 3's slider), with a 4 dp track, a short handle and no dot at the end, which `ProgressSlider` doesn't let a caller change.
  - Add/Edit form reviewed against Material 3 Expressive; agreed with the user (2026-11-10):
    - A large top app bar titled "New memory" or "Edit memory", with Clear (Discard while editing) in it; Save pinned above the tab bar, so it is always in reach (the wavy progress takes its place while saving).
    - No collapsible cards: the date, the fragments and the photos are sections under small headers, as on the memory page; wording in sentence case ("Select on map", "All day", "Same as the memory").
    - The location as a list item row (pin icon, place and address, tap to pick on the map); a missing title or location shown as an error on its field instead of a toast, and a failed save as a Snackbar.
    - Dates as "Starts" and "Ends" rows with a tappable date and time, under an "All day" switch, picked with Material 3's Compose date, date range and time pickers (the weeks must still start on the phone's first day; if the Compose picker can't, the Material Components dialogs stay).
    - The marker colour as on Settings: a card with the colour as a strip across its top, the swatches (ring and check mark when selected), and the sliders (no tick dots, no stop indicator, outlined handles) opening under its header.
    - Each fragment as one flat card: colour strip, place row, its own dates and colour, a plain delete icon; "Add a memory fragment" under the list.
    - Photos as a grid of rounded tiles under a "Photos and videos" header with the count, each with a small remove button in its corner; new picks first.
    - Theme colours throughout, in light and dark mode.
  - Add/Edit form moved (2026-11-10): `ui/add/` (`AddMemoryContent`, `FormDates`, `FormParts`), with the marker colour's controls shared with Settings in `ui/color/`. The form's adapters (`MemoryFragmentEditAdapter`, `FragmentEditPickers`, `SelectedMediaAdapter`, `ColorPresetAdapter`) and layouts are gone, and with them the time picker helpers of `util/PickerListeners.kt` and `util/PhoneCalendar.kt` (the map's date filter still uses the date picker ones).
    - The colour sliders' handles have an outline, on Settings too, so a black or white one shows on the page (it was left for when the form moved).
    - Material 3's date pickers read the first day of the week from the locale, so the form gives them the phone's language with Android's first-day preference added (the Unicode "fw" extension). The emulator's US region starts weeks on Sunday either way: a phone set to start on Monday is not checked yet.
    - The date range picker's headline shows the days without their year, which otherwise wrapped onto two lines.
  - Location picker reviewed against Material 3 Expressive; agreed with the user (2026-11-10):
    - Its map moves too, to Google's Compose map library (`maps-compose`), so the bars over it set the map's padding instead of measuring it.
    - A search bar across the top with a back arrow (Back was the only way out); tapping it still opens Google's place search screen.
    - "My location" as a floating action button at the bottom right, and a panel at the bottom with the picked place's name and address (or coordinates), "Use this location", and "Tap the map or search for a place" while nothing is picked (the button used to look usable then); a loading indicator while the address is looked up.
    - The marker in the theme's primary colour, without an info window; a spot is never named by its plus code ("G23R+C23") or by its house number alone.
    - Google's zoom buttons stay; "place search isn't available" as a Snackbar; wording in sentence case.
  - Location picker moved (2026-11-10): `ui/location/` (`PickLocationContent`, `PickLocationActions`), in `PickLocationFragment`'s `ComposeView`, its ViewModel on `StateFlow<PickLocationUiState>`, the picked spot still in `SavedStateHandle` and the camera in `rememberSaveable`. The fragment keeps the place lookups and the "my location" flow; its layout is gone.
  - Map reviewed against Material 3 Expressive; agreed with the user (2026-11-10):
    - The map moves to `maps-compose`, with its clustering composable (`Clustering` in `maps-compose-utils`) and the app's own marker renderer (the same pins, cluster circles and colours). That composable clears and re-adds every item when the list changes, so [PERF-8](#perf-8)'s "only the markers that changed" goes; accepted for the simpler code. If the map gets slow with many memories, the fallback is to keep the app's own `ClusterManager` and `MarkerSync` inside the Compose map (`MapEffect`): see PERF-8's note.
    - The filter bar as one Material 3 Expressive button group (quick filters, the dates, colour statistics).
    - The quick filters in Material 3's menu, the selected one checked; their labels in sentence case ("Last 7 days", "All time"), which Settings' default-filter menu shares.
    - The date range picker as Material 3's Compose one, as on the Add form (`WithPhonesWeek`).
    - "My location" as a floating action button at the bottom right, as in the location picker.
    - The colour statistics as a donut drawn in Compose (the pie chart library goes), with the counts per colour and the total in the middle; the ❤️ stays, under the total.
    - A marker's memories in a panel at the bottom, as in the location picker, as list rows like the Timeline's with the colour as a strip on the left; tapping the map still closes it.
    - Google's zoom buttons stay; wording in sentence case; theme colours in light and dark mode.
    - To fix on the way: a pin shared by several memories is read out only as "Map Marker", not "3 memories here" (maybe behind [TEST-4](#test-4)'s flaky map test), and the map doesn't zoom to its markers when it opens on the default filter, so a pin can sit half under the filter bar.
  - Map moved (2026-11-11): `ui/map/` (`MapContent`, `MapControls`, `MapActions`, `MarkerRenderer`), in `MapFragment`'s `ComposeView`, its ViewModel on `StateFlow<MapUiState>` with the camera moves as requests in that state. The map's layout and overlay item, `MemoryOverlayAdapter`, `MaxHeightCardView`, the pie chart library, `MarkerSync` with its test, and the Material Components date picker helpers (`util/PickerListeners.kt`, `startingWeeksOnPhonesFirstDay`) are gone. Every screen is now in Compose.
    - A pin shared by several memories was named only "Map Marker" once it was drawn again (`onClusterItemUpdated` gave it the pin's own title, which is empty for several memories); it now keeps "3 memories". Its name is in sentence case now.
    - The map shows all its pins the first time it has some, unless it goes to where the phone is, so no pin opens half under the filter bar. It also shows them all when it has location access but the phone can't say where it is (location off), where it used to stay on the whole world with the pins in one cluster or under the filter bar.
  - Map and location picker, changed after trying them (2026-11-11, agreed with the user):
    - Google's zoom buttons and the "my location" button became one Material 3 floating toolbar of the app's own (`ui/map/MapToolbar`: zoom in, zoom out, my location) on both maps. It floats over the map, so the map keeps room only for the panels and Google's logo is back next to the tab bar (the button's room had lifted it far above). Where the screen is wide enough (a phone turned sideways) the toolbar sits at the right edge beside the panel instead of above it, since the two don't fit a turned phone's height; the panel stays centred, as wide as the filter bar (the picker's search bar is capped to the same width), so they line up (`ToolbarAndPanel`). The map keeps room for a panel only where it would cover Google's logo (upright): exactly the panel's height as it opens and closes, the gap above it included, so the logo and the toolbar move with the panel instead of jumping when it has closed. Sideways it keeps none, so nothing moves; a place it shows goes in the part of the map above the panel instead (`cameraTargetFor`). A camera move cancelled by turning the phone stays requested and runs on the rebuilt screen (it used to be dropped, leaving the map on its first view). Google's own my-location button can't replace it: it appears only once location access is granted, so it can't ask for it.
    - The filter bar, the toolbar and the picker's search bar share the panels' colour, a little see-through (`mapControlColor`); the toolbar has no shadow, which would show through.
    - "My location" never zooms out: from the area around the phone (or anywhere closer) a press shows its streets.
    - Tapping the Map tab while the map shows closes the panel or the colour statistics, and with nothing open goes back to the home view (the area around the phone with location access, otherwise all the memories shown), as the Timeline's tab goes back to its top.
    - The colour statistics open and close as the quick filters' menu does: growing from their button and fading.
  - Step 6, the Compose shell, agreed with the user (2026-11-11), in three phases: (1) the fragments become thin hosts, and what they did for their screen moves to Compose and the ViewModels; (2) a Compose shell: `NavigationSuiteScaffold` (a short navigation bar, a rail in landscape), the tabs kept alive in a host of the app's own (a visited tab stays composed, paused by capping its lifecycle), Navigation 3 for the screens over the tabs (memory, media viewer, location picker, the missing-photo sheet) with predictive Back, fading between tabs and sliding detail screens, Compose snackbars and dialogs; (3) edge to edge ([UX-13](#ux-13)) and polish.
    - `activity-compose` is at 1.13.0 (it was 1.8.2), on its own commit first; the unit tests, detekt and lint pass with it.
    - Phase 1: each screen has a `…Route` composable (`MapRoute`, `PickLocationRoute`, `MemoryRoute`, `MissingMediaSheetRoute`, `AddMemoryRoute`, `SettingsRoute`) that takes its ViewModel and does what the fragment did: result launchers (`rememberLauncherForActivityResult`, the photo picker, permissions, Drive consent), the questions (`ui/common/ConfirmDialog`, Material 3's `AlertDialog`, open state in `rememberSaveable`; it replaces the DialogFragment `util/ConfirmDialog`), the "my location" flow (`ui/common/MyLocation.kt`, replacing `util/MyLocationAction`), the "Re-link" flow (`ui/common/RelinkRequester`, replacing `util/RelinkAction`) and sign-in (`ui/settings/AccountFlow`). The fragments only host the route and pass on the activity's requests. What the Map and the Add form decided in their fragments (the first zoom, focus requests, picked media, edit requests) moved into their ViewModels.
    - Left for phase 2: the memory pager's `ViewPager2`, the media viewer's hiding of the tab bar, and the missing-photo sheet as a `BottomSheetDialogFragment` (as a Compose sheet it needs a ViewModel of its own per open sheet, which Navigation 3's entries give).

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

### <a id="build-3"></a>✅ BUILD-3 · Low · Supply-chain hygiene
- **What:** GitHub Actions are pinned to tags (`@v4`) rather than SHAs; `gradle-wrapper.properties` has no `distributionSha256Sum`.
- **Status:** Fixed (2026-11-06).
  - Every action in the workflows is pinned to the commit its tag pointed to, with the exact version in a comment (`actions/checkout@3d3c42e… # v7.0.1`), so a tag moved to other code can't change what CI runs. Dependabot (`.github/dependabot.yml`) keeps them current: once a week it opens one PR that updates every pinned commit and its version comment.
  - `gradle-wrapper.properties` has the distribution's `distributionSha256Sum`, from Gradle's published checksum: the wrapper refuses a download that doesn't match (checked with a wrong value). The wrapper jar itself is checked on every CI run by `setup-gradle` (`validate-wrappers`, on by default).

### <a id="build-4"></a>✅ BUILD-4 · Low · Robolectric cannot run against `targetSdk 37`
- **Where:** noted in the re-linking design notes (then `SIGNATURE_FIX_PLAN.md` §5)
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

### <a id="test-2"></a>✅ TEST-2 · Medium · Critical logic is untested
- **What:** No tests for `MediaHasher` (BUG-9), backup zip/unzip/restore (SEC-3, SEC-4, DATA-5), the clustering and date filter in `MapFragmentViewModel` (UX-9, UX-10), `saveMediaItems` dedup and grant release, the DAO queries, or the undo flow (DATA-2).
- **Status:** Fixed (2026-11-07). Every area listed has tests:
  - `MediaHasher`: `MediaHasherTest`.
  - Backup zip, unzip and restore: `BackupZipTest`, `BackupCompatibilityTest` and `RestoreCompatibilityTest`.
  - Clustering and the date filter: `MarkerClusteringTest` and `MapFragmentViewModelTest`.
  - DAO queries and the undo of a delete: `MemoryGroupDaoTest`.
  - Saving a memory's media: `SaveMediaTest` saves the Add form into an in-memory database. It covers the same photo picked through two links (saved once), a photo whose file can't be read, the numbering without gaps, photos kept through an edit keeping their signature and details, and an edit giving up the grants of the photos it removed or merged, unless another memory still uses them.
  - Making that test possible changed two things. `PerfUtil.trace` runs its work untraced where Firebase isn't set up (unit tests), so tracing can never fail the work it measures. `releaseUriGrantsIfUnused` walks a copy of the grant list: Robolectric hands out its live list, so releasing a grant threw, and `UriGrantReleaseTest` had only passed because the grant it released came second to last.

### <a id="test-3"></a>✅ TEST-3 · Low · No UI or instrumentation tests
- **What:** Only the template `ExampleInstrumentedTest` exists. Espresso is declared but unused.
- **Blocks:** [ARCH-1](#arch-1), [PERF-7](#perf-7) and [ARCH-10](#arch-10) (Compose migration). **None of them may start until this item is complete:** the entire UI covered, with every user flow and as many edge cases as possible. Covering only the screens or flows a change touches is not enough.
- **Fix:** Add instrumented UI tests for the whole app, run in CI on an emulator (for example Gradle managed devices), so that "covered" means "passing". At least:
  - **Navigation:** every tab switch; back from every screen (memory pager, media viewer, location picker); rotation on every screen and with dialogs and pickers open; process death and restore on every screen.
  - **Map:** markers and clusters; tapping a marker or a cluster and the overlay it opens; opening a memory from the overlay; every date filter option, including overlapping ranges; the colour pie chart; the "show fragments" and "cluster markers" settings taking effect; place search; the "my location" flow (explanation, granted, denied, permanently denied with the offer to open settings); no memories.
  - **Timeline:** grouping by day, search with and without matches, no memories, opening a memory, "Show in timeline" scrolling and flashing.
  - **Add/Edit:** a new memory with every field (title, description, location, all-day and date range, date and time pickers, colour and presets, adding, removing and reordering photos and videos, and fragments with their own location, dates and colour, added, edited, reordered and removed); validation errors; the unsaved-changes prompt; Edit from a memory, also while another memory is being edited; leaving the tab with and without changes and while saving; save errors.
  - **Location picker:** tapping the map, place search, "my location", confirm, cancel and back, reverse geocoding failing (offline).
  - **Memory pager:** swiping between memories; Show on map, Show in timeline and Edit, both when the target tab already has a view and when it doesn't; delete then Undo; delete then dismiss; media from another device (error tile); opening the media viewer, zooming, playing a video.
  - **Settings:** not configured, signed out, signing in and signing out; backup list and paging; manual backup; restore with its confirmation and failure; deleting a backup with confirmation; the automatic backup status line; colour presets added, edited, reordered and deleted; map preferences and the default filter; re-linking media; sending logs.
  - **Across screens:** data changed on one tab shows on the others; a restore reaches every screen.
  - **Edge cases:** empty database, a large database, no network, dark mode, very long texts, and anything a past BUG or UX finding fixed.
- **Make the tests outlive the UI code:** they are the safety net for ARCH-10, so they must keep running when a screen moves to Compose. Find elements by what the user sees (text, content descriptions, test tags exposed as resource ids), for example with UI Automator, or keep all element lookups in one screen-object layer per screen so only that layer changes. Tests written straight against View ids with Espresso would have to be rewritten during the migration and would prove nothing.
- **Changing tests during the migration:** a test may change only together with a deliberate change in behaviour or look that is objectively better than the old one (the rule in [ARCH-10](#arch-10)). The test is then updated in the same commit to describe the new behaviour, and the commit message says why. A test is never edited only because a migrated screen fails it: the screen is fixed instead.
- **Test seams needed:** Google sign-in, Drive, the system photo picker and location are outside the app. Give them fakes the tests can inject (signed-in account, a Drive with backups, picked media URIs, a fixed location).
- **Status:** Fixed. The setup is in place: instrumented tests with UI Automator behind one screen object per screen, the Test Orchestrator with a fresh install for every test, fakes for sign-in, Drive, the photo picker, the location and place search (`AppServices`), helpers for test data, and a dedicated local emulator (API 37 with Google Play, signed in) that `scripts/ui-tests.sh` runs them on ([docs/ui-tests.md](docs/ui-tests.md)). CI doesn't run them: its fresh emulators never get Google's latest map renderer, which Google Play services downloads only on a device set up with Google Play, so the map tests fail there (the `ui-tests.yml` workflow on Gradle managed emulators is only started by hand). The navigation tests cover every tab switch, back from every tab and detail screen, tapping a tab while a memory, the media viewer or the location picker is open, Show on Map, Show on Timeline and Edit (with the target tab opened before and not), the location picker round trip, and rotation on every screen and with the date and time pickers open. Process death is covered on the same screens and pickers (`ProcessDeathTest`), simulated with "Don't keep activities": Android rebuilds the screens from their saved state alone and clears their ViewModels, as after a real process death. The process itself keeps running, so the app's own start-up after a kill (such as the re-link on a cold start) is not covered. Those tests found [UX-17](#ux-17) and showed what [UX-7](#ux-7) lost. The Timeline tests cover grouping by day, search (title, description, place, address; no matches; cleared), no memories, the Scroll to top button and Show on Timeline scrolling to a memory far down; they found [BUG-17](#bug-17). The memory screen tests cover swiping between memories, delete with Cancel, Undo (the photo and the fragment come back) and the final delete, a photo from another phone with its own Re-link button (asking for access, then showing the photo), reordering photos and fragments by dragging, and a fragment's Show on Map opening the map at the fragment. The Settings tests cover signing in (and a failed sign-in) and out, Backup Now, a backup restored into changed data, a broken backup that fails to restore, deleting a backup with Cancel and Delete, an automatic backup after a change with its status line, the map preferences and default filter staying set, re-linking a photo brought from another phone, and Send Debug Logs opening the share sheet; they found [UX-18](#ux-18). The colour preset tests cover selecting a preset, a saved change, Undo, adding the sliders' colour, deleting with Cancel and Delete, and reordering by dragging. The Map tests cover a marker per memory, tapping a marker (the overlay, opening the memory from it, tapping the map to close it), one marker for memories at one place, clustering and turning it off, tapping a cluster zooming in until its memories show, no memories, fragment markers and their setting, every quick date filter including a memory that only overlaps the range, picked dates, the default filter from Settings, opening and closing the colour statistics, and the colours their chart shows (read from a screenshot), following the date filter. The media viewer tests cover opening at the tapped photo, swiping to the next and previous photo, and zooming in with a double tap. The location tests (the location is a fake, `AppServices.location`; access goes through Android's real dialog) cover the map and the picker asking only when "my location" is tapped, Not now, access allowed, access refused twice and the offer to open Settings, the map opening where the phone is and the picker starting there once access is granted, and the message when the phone's location is off. The Add form tests (the photo picker is a fake, `AppServices.mediaPicker`) cover a new memory saved with a description, location and photos (it opens and shows in the timeline), saving without a title or location, removing a picked photo, a cancelled picker, Clear with Cancel and Clear, an edit updating the memory instead of copying it, the question before an edit replaces a new memory (Keep editing and Discard), leaving an unchanged and a changed edit, a picked start date, the end date and time with the rule that keeps the end after the start, an all-day memory, removing a photo while editing, and Back from the location picker keeping the place the memory had. The media access tests cover re-linking asking for access and then finding a photo another app saved, a restore without access that still restores and then offers access for the unlinked photo (Allow access and Not now), and Cancel on the explanation before a restore. Paging through many backups happens inside the Drive storage, which the UI tests replace; `DriveBackupStorageTest` covers it. The fragment and colour tests cover a fragment with its own place saved with the memory, a new fragment starting at the memory's place, saving refused while a fragment has no location (until it is removed), a fragment's own dates (also all day, with its own range of days) and colour, and a marker colour picked from a preset. Saving an edit of a memory that was deleted meanwhile saves it as a new memory (`SaveErrorTest`, which found [BUG-18](#bug-18)). Other save errors (a full disk, a broken database) can't be caused on purpose. The video tests (the videos are five seconds of one colour, encoded on the phone) cover a picked video saved as a video, and playing and pausing one in the viewer. (The form keeps fragments in the order they were added; they are reordered on the memory screen.) The Settings restore test also checks that the restored data reaches the map. The timeline tests also cover the flash after Show on Timeline (read from screenshots, with animations turned on for that test), and the location picker tests a spot picked offline, which is kept as coordinates when its address can't be looked up (`MemoryMapAppRule.goOffline`), and place search (a fake, `AppServices.placeSearch`, so no test calls Google's paid Places API): a searched place picked with its name and address, a search the user leaves, and search without a Maps key. Google's own search screen itself is not tested. The edge case tests cover very long texts (shown in full and kept through an edit), a large database (500 memories: the map clusters them, and the timeline lists and searches them) and dark mode (every tab and the memory screen turn dark; the map's own tiles are not checked). Android 14 (minSdk) can't be tested on an emulator: every API 34 image ships Google Play services 23.18, whose map renderer crashes on the app's markers, so it needs a real device or Firebase Test Lab. The restore question stays open through a rotation and process death (plain dialogs used to close, [UX-16](#ux-16)). The Settings tests also cover signing in to a build without an OAuth client ID, and the Add form tests a save that finishes after the user left the tab: it says so without opening the memory and leaves the form empty (this found [BUG-19](#bug-19)). Every flow listed above has a test, and the whole suite (90 tests, none skipped) passed in one run on the local test emulator on 2026-11-02. Not covered on purpose: save errors that can't be caused (a full disk, a broken database), Google's own place search screen, and the map's own tiles in dark mode.

### <a id="test-4"></a>⬜ TEST-4 · Medium · Flaky UI tests
- **What:** Two UI tests fail now and then on the local test emulator and pass when run again, so a red run doesn't always mean a broken app, and a real failure in them could be taken for flakiness:
  - `MapTest.memoriesAtOnePlaceShareAMarker`: "The map overlay with "Madrid trip" is not shown" (once in a full run, 2026-11-10; it passed alone right after).
  - `FragmentsAndColorTest.anAllDayFragmentKeepsItsOwnDays`: the fragment's dates stay today's ("Nov 10, 2026") instead of the range typed into the date range picker ("Oct 21, 2026 - Oct 23, 2026"); twice on 2026-11-10, in full runs, passing alone each time. The typed range seems not to reach the picker, or its OK runs before it does (`screens/Dialogs.kt`).
  - `MapTest.tappingTheMapTabAgainClosesTheOpenCardThenShowsAllTheMemories`: "The map overlay with "Athens trip" is not shown", the same as above (once in a full run, 2026-11-11; it passed when its class ran again).
  - `AddMemoryTest.aMemoryIsSavedOnceItHasATitleAndALocationAndOpensWithItsPhotos`: "A thumbnail that could not be loaded is still shown" (once in a full run, 2026-11-11; it passed when its class ran again).
- **Fix:** Find the cause from the failed runs' screenshots, element trees and logcat, and make the screen objects wait for what the test needs (the overlay, the picker's fields holding the typed dates) instead of retrying the test.

[↑ Back to index](#index)

---

## I18N / A11Y

### <a id="i18n-1"></a>✅ I18N-1 · Medium · All UI text is hard-coded
- **Where:** `res/values/strings.xml` holds only `app_name` and `map_id`. There are about 70 literal `android:text` values in layouts, plus Kotlin literals (Toasts, dialogs, status messages, `DateFilterOption` labels, "N Memories" in `Markerable.kt:48`, which is also not pluralized).
- **Fix:** Move the text to `strings.xml` and `plurals`. Store `DateFilterOption` by enum name (already done) and look up labels from resources.
- **Compose migration ([ARCH-10](#arch-10)):** the about 70 literals in layouts go away with the XML. Moving them to `strings.xml` first is not wasted, since Compose reads the same resources (`stringResource`). At the latest, each migrated screen must take all its text from resources and add no new literals.
- **Status:** Fixed (2026-11-08). Every text the app shows comes from `res/values/strings.xml`, so adding a language is a `values-xx/strings.xml` (there are no translations yet).
  - Counts use plurals (`memories_here`, `memories_nearby`, `items_selected`, the re-link results). Text built from parts uses patterns with placeholders: date ranges, "Signed in as", coordinates and their hemisphere letters, a place above its address.
  - Each date filter option names a string resource (`DateFilterOption.label`); the map and Settings keep the option itself, never its label, so a translated label can't break the stored default filter.
  - Messages that helpers hand to the screens (re-link progress and results, backup and restore progress) are read from resources where they are made.
  - The missing-photo sheet's time differences are written by ICU's `MeasureFormat` in the phone's language, as the dates are. The hue next to the colour sliders follows the phone's digits, like saturation and brightness.
  - The texts stay the same, except: "..." became "…"; a photo that can't be shown says "Photo" or "Video" on its tile instead of the code's "IMAGE" or "VIDEO"; one picked photo reads "1 item selected"; time differences read "7 sec" and "1 hr, 5 min" instead of "7 s" and "1 h 5 min". The UI tests that read these follow.
  - Fixed on purpose: what is stored or looked up rather than shown (the Drive folder name, backup file names, saved-state and dialog keys), the logs and the re-link diagnostics.

### <a id="i18n-2"></a>⬜ I18N-2 · Low · RTL disabled
- **Where:** `AndroidManifest.xml:29` (`supportsRtl="false"`)

### <a id="a11y-1"></a>✅ A11Y-1 · Medium · Missing content descriptions
- **What:** Only 6 `contentDescription`s among about 59 image and button views (thumbnails, colour swatches, play/pause, chevrons, pick-media, colour indicators).
- **Note:** the map's quick-filter and colour-statistics buttons, the media thumbnails and the full-screen viewer have descriptions now (added for the UI tests). So do the colour preset swatches: their hue, saturation and brightness as the sliders show them, and whether they are selected. And the remove buttons on the Add form's picked photos ("Remove photo", "Remove video"), the video player's button ("Play", "Pause"), and the map's clusters ("4 memories nearby").
- **Also check:** the Material sliders (the colour sliders in Settings, the Add form and the fragments) report themselves as not visible to accessibility services, so UI Automator skips them. TalkBack may skip them too; check with TalkBack whether they can be reached and changed.
- **Compose migration ([ARCH-10](#arch-10)):** Compose's `Icon` and `Image` require a content description, so the migration forces this anyway. Better to do it before: the UI tests (TEST-3) find elements by content description so that they keep working after the migration, so descriptions added now (as string resources) help those tests and carry over to Compose unchanged.
- **Status:** Fixed (2026-11-06). An audit of every screen found most controls named already (the descriptions added for the UI tests, many set in code). Two gaps were left, now named with string resources:
  - The colour sliders (Add form, each fragment, Settings): a Material slider hides itself from accessibility services but shows its thumb as an element of its own, which TalkBack can reach and change. Without a content description it read only "Value, 120"; it now reads "Hue", "Saturation" or "Brightness" first.
  - The video player's position bar ("Video position").
  - `edge/ControlNamesTest` keeps it that way: on the map (with a marker's memories and the colour statistics), the timeline, Settings (signed in, with a backup, presets open and one being edited), the Add form (a photo, a video, a fragment, colours open), the location picker, a memory, the media viewer (photo and video) and the missing-photo sheet, every element that can be tapped, long-pressed, switched, typed into or slid has a name (`screens/ControlNames`; Google's map elements are left out).
  - Pictures that only show a colour (the colour indicators, the timeline bars) are not controls and have no name: that colour is their only signal is [A11Y-2](#a11y-2).

### <a id="a11y-2"></a>⬜ A11Y-2 · Low · Colour is the only signal
- **What:** Marker colour, the pie-chart stats and the timeline colour bars carry meaning only through colour. The preset outline uses fixed `Color.BLACK` / `LTGRAY` (`ColorPresetAdapter.kt:42`), which is invisible on a dark theme.
- **Fix:** Add labels or patterns, and use theme attributes for the outlines.
- **Compose migration ([ARCH-10](#arch-10)):** the preset outline lives in `ColorPresetAdapter`, which the migration removes; the Compose version should use the theme's outline colour. Labels or patterns for markers, the pie chart and the timeline are a design change that doesn't depend on the migration.

[↑ Back to index](#index)
