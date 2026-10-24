# Database (Room, `MemoryMapDatabase.kt`, version **18**)

Tables:
- `memory_groups` (`MemoryGroup`)
- `memory_fragments` (`MemoryFragment`, FK `groupId` → group, `ON DELETE CASCADE`)
- `media_items` (`MediaItem`, FK cascade)
- `hsv_presets` (`HSVPreset`)

Rules when changing the schema:
1. Bump `DB_VERSION`, then add an `AutoMigration` (with a spec for renames or deletes) or a manual `Migration` registered in `MemoryMapDatabase.build()` (used for the app's database and for backup copies).
2. Commit the new `app/schemas/.../<n>.json`.
3. Remember that **backups are raw database files** and every existing backup must stay restorable. Restore runs migrations from whatever version the backup has (1 to current), so never remove a migration and keep the chain complete. There is no destructive fallback: a missing migration makes Room throw instead of wiping data. Before overwriting anything, restore rejects only backups newer than `DB_VERSION` (`backup/BackupCompatibility.kt`, which reads the SQLite header and `metadata.json`'s `dbVersion`; metadata version 1 backups don't have `dbVersion`). `RestoreCompatibilityTest` restores a database of every version from 6 up; it must keep passing.
4. Avoid `ALTER TABLE … DROP COLUMN` in manual migrations (it needs SQLite 3.35+, but `minSdk` is 27; see `DATA-9`).
5. **Adding a table:** add it to `DatabaseContents`, `DatabaseContentsDao` (read, delete, insert, `replaceAll`) and `DatabaseContents.TABLES`. Restore copies rows table by table, so a table missing there is silently dropped on restore (`RestoreCompatibilityTest` fails if `TABLES` doesn't match the latest schema).
6. Open database copies (backups) only through `MemoryMapDatabase.withCheckedCopy`. A plain open of a damaged file makes Android delete it and SQLite create an empty database, which would look like a backup with no memories.

Other data notes:
- Dates are `ZonedDateTime`, stored as ISO strings (`Converters`). All-day memories use the start of the day through `LocalTime.MAX` in the system zone.
- `order` columns (media, fragments, presets) are nullable. Sort by `order`, then fall back to the date (the comparators in `fragment/MemoryFragment.kt` and `fragment/AddMemoryGroupFragment.kt`).
- `Markerable` is the shared interface of groups and fragments for the map, overlay and clustering (`MarkerableCluster`, `MarkerableDiffCallback`).
- Editing a memory **deletes and re-inserts** its media and fragment rows (their ids change on every save).
- DAOs are wrapped in a reflection proxy that adds a Firebase trace to every call (`PerfUtil.tracedDao`). Always get DAOs through `memoryGroupDao()` / `hsvPresetDao()`, not the `…Internal()` accessors.
