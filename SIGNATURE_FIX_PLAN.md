# Robust Media Re-linking and Stable Hashing Strategy

This plan outlines the diagnosis, risk assessment, diagnostic build, and safe resolution strategy for media file re-linking issues in Memory Map.

## Problem Context

When media files are selected via Android's Photo Picker (`PickMultipleVisualMedia`), system-provided URIs are temporary (`content://media/picker/...`). Over time, after app restarts or reboots, read permissions on these picker URIs expire.

When the app attempts to re-link missing media via `LocalMediaUtil.verifyAndFixMediaItems`, two issues prevent successful candidate matching:
1. **Photo Picker EXIF Redaction:** Android redacts EXIF location metadata on-the-fly for picker URIs unless `ACCESS_MEDIA_LOCATION` is granted. This causes the recorded `fileSize` (from redacted stream) to be slightly smaller (by ~100–500 bytes) than the actual file size stored in `MediaStore.MediaColumns.SIZE` on disk.
2. **Rigid Querying in `LocalMediaUtil`:** The current candidate lookup queries MediaStore with `WHERE SIZE IN (fileSize)`. Because the recorded `fileSize` reflects the redacted size, MediaStore returns 0 candidates, causing re-linking to fail with `"No local media found with signature..."`.

---

## Distribution Model & Permission Environment

- **Manual APK Distribution:** The app is distributed directly as an APK to users (not via Google Play Store).
- **Play Store Policies:** Google Play Store permission declaration restrictions do not apply.
- **User System Experience:** Although Play Store policies are absent, standard Android OS runtime permission prompts (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `ACCESS_MEDIA_LOCATION`) are presented to the user. Broad permission prompts should still be minimized where possible for a clean user experience.

---

## Critical Risks & Backup Compatibility Assessment

### Potential Risks of Full Refactor & Signature V2 Scheme:
1. **Non-deterministic Redacted Hash Simulation:** Attempting to "simulate" Android's internal Photo Picker EXIF redaction on MediaStore candidates is non-deterministic. Redaction logic varies across Android OS versions (13, 14, 15) and OEM systems (Samsung OneUI, Pixel, Xiaomi).
2. **Existing Backup Restores:** Old backups generated on previous app versions or other devices contain legacy signatures and `mediaSignatureV2 = null`. If legacy signature matching fails due to EXIF size shifts, restoring old backups on new builds will fail to re-link media files.
3. **Database Schema Migrations:** Adding `mediaSignatureV2` requires Room DB schema migrations. Schema mismatches during restore operations risk data corruption or failed restores.

---

## Phase 1: Diagnostic Build & Root Cause Confirmation

Before executing a permanent fix, a diagnostic build will be released to the active user to confirm the exact root cause on their device.

### 1. Detailed Diagnostic Logging in `LocalMediaUtil`
- Log full metadata for every missing `MediaItem` (`id`, `uri`, `deviceId`, `fileSize`, `dateTaken`, `mediaSignature`).
- When querying MediaStore, log **all** candidate files matching `dateTaken` (exact or ±10s) or `fileSize` (within ±10%).
- Log the exact `_ID`, `_display_name`, `SIZE`, `DATE_TAKEN`, and computed signature for each candidate, along with the size difference (`candidate.size - item.fileSize`).

### 2. UI Diagnostics for Missing/Unloadable Media
Update thumbnail previews (`item_media_thumbnail.xml` and `item_media_selected.xml`) when a file cannot be loaded or is from another device:
- Display a smaller red warning alert icon at the top/center of the preview.
- Display a text overlay beneath the warning icon containing:
  - **Type:** `IMAGE` or `VIDEO`
  - **File Size:** Formatted string (e.g. `2.8 MB`)
  - **Date Taken:** Formatted date & time (e.g. `2026.08.20 21:43`)
- This allows the user or developer to visually identify broken media items and cross-reference them with files in the device Gallery.

---

## Phase 2: Safe, Low-Risk Resolution Strategy

Once diagnostic logcat output confirms candidate size/time deltas on the device:

### 1. Candidate Discovery via `dateTaken` + Size Tolerance
- Query MediaStore primarily using `DATE_TAKEN` (exact or ±2s) and secondary size tolerance (e.g. `abs(candidate.size - item.fileSize) < 10KB`).
- Photos and videos preserve their `DATE_TAKEN` timestamp across device restores, Google Drive backups, and Photo Picker selections.

### 2. Canonical MediaStore Re-linking
- Convert temporary Photo Picker URIs to permanent canonical MediaStore URIs (`content://media/external/images/media/<id>`) upon selection or during re-linking.

### 3. 100% Backward Compatibility
- Requires **no database schema changes** (`mediaSignatureV2` is avoided).
- Guarantees **100% compatibility for old backups** created on previous app versions or other devices.
