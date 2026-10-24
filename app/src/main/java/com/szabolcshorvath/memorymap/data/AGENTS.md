# Database (Room, `MemoryMapDatabase.kt`, version **18**)

Tables:
- `memory_groups` (`MemoryGroup`)
- `memory_fragments` (`MemoryFragment`, FK `groupId` → group, `ON DELETE CASCADE`)
- `media_items` (`MediaItem`, FK cascade)
- `hsv_presets` (`HSVPreset`)

Rules when changing the schema:
1. Bump `DB_VERSION`, then add an `AutoMigration` (with a spec for renames or deletes) or a manual `Migration` registered in `getDatabase()`.
2. Commit the new `app/schemas/.../<n>.json`.
3. Remember that **backups are raw database files** and every existing backup must stay restorable. Restore runs migrations from whatever version the backup has (1 to current), so never remove a migration and keep the chain complete. There is no destructive fallback: a missing migration makes Room throw instead of wiping data. Before overwriting anything, restore rejects only backups newer than `DB_VERSION` (`backup/BackupCompatibility.kt`, which reads the SQLite header and `metadata.json`'s `dbVersion`; metadata version 1 backups don't have `dbVersion`).
4. Avoid `ALTER TABLE … DROP COLUMN` in manual migrations (it needs SQLite 3.35+, but `minSdk` is 27; see `DATA-9`).

Other data notes:
- Dates are `ZonedDateTime`, stored as ISO strings (`Converters`). All-day memories use the start of the day through `LocalTime.MAX` in the system zone.
- `order` columns (media, fragments, presets) are nullable. Sort by `order`, then fall back to the date (the comparators in `fragment/MemoryFragment.kt` and `fragment/AddMemoryGroupFragment.kt`).
- `Markerable` is the shared interface of groups and fragments for the map, overlay and clustering (`MarkerableCluster`, `MarkerableDiffCallback`).
- Editing a memory **deletes and re-inserts** its media and fragment rows (their ids change on every save).
- DAOs are wrapped in a reflection proxy that adds a Firebase trace to every call (`PerfUtil.tracedDao`). Always get DAOs through `memoryGroupDao()` / `hsvPresetDao()`, not the `…Internal()` accessors.
