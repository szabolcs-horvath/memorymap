# Media Re-Linking: Findings and Design

This document records what the real-device logs and emulator tests showed about media re-linking,
and the design that follows from it. It replaces earlier versions of this plan, whose root-cause
theories (EXIF size shifts, expiring photo picker URIs) did not hold up.

---

## 1. Evidence

### Real device (Samsung SM-A336B, Android 14)
* **Diagnostic build, 2026-10-06:** every logged item was a photo picker URI from this installation,
  and all 86 re-linked by exact size + signature. Picker id `N` always matched MediaStore id `N`.
  The logcat buffer had rolled over, so items before id 4990 are not in the log.
* **No size shift:** all 64 MediaStore candidates taken within ±15 s of an item had size delta 0,
  date delta 0 and an identical signature. No candidate was a few hundred bytes off.
  The near-size entries in the log are unrelated photos from the ±10 % size window.
* **No URI access errors:** neither log contains a permission denial, `SecurityException` or
  load failure for any media URI.
* **v35, 2026-08-20:** four re-link failures (`No local media found with signature ...`).
  The log line only printed the signature, and none of them appear in the October log,
  so they cannot be identified.
* **Affected user, after the re-linking release (reported 2026-10-25):** same phone, full media access,
  and no item re-linked. For one item found by date, the gallery reported about 4.6 MB while the app
  had stored about 5.3 MB. Units can't explain a gap that size (MB vs MiB is under 5 %), so the file on
  the phone is probably not the bytes that were picked. Re-linking only queries exact stored sizes, so
  that file was never a candidate. The cause is not known yet; the diagnostics report (§3) is meant to find it.

### Emulator (API 36, two JPEGs with GPS EXIF)
| Read path | Size | GPS bytes | Signature |
|---|---|---|---|
| Original file (unredacted) | 821,703 | intact | `821703_20d578…` |
| App, MediaStore URI | 821,703 | zeroed | `821703_6e2708…` |
| App, photo picker URI | 821,703 | zeroed | `821703_6e2708…` |

* **EXIF redaction is zero-fill in place.** The size never changes. Picker and MediaStore reads are
  redacted identically (the app does not hold `ACCESS_MEDIA_LOCATION`), so same-device signatures match.
* **Picker grants survive a reboot.** `takePersistableUriPermission` has been called since the initial commit.
* **MediaStore URIs need the broad permission; picker URIs do not.** With `READ_MEDIA_*` revoked,
  picker URIs still opened and `content://media/external/...` URIs threw `SecurityException`.
* **The picker reports a synthetic `DATE_TAKEN`.** For a photo without an EXIF date, the picker
  returned the file time while MediaStore returned 0. Stored `dateTaken` values are not reliable
  enough to match on.

### Database snapshot
* `content://media/external/images/media/38` was stored for two different photos (different
  signatures, different installations). MediaStore ids are per device.

---

## 2. What actually breaks re-linking or display

1. **Another device.** URIs (picker or MediaStore) are meaningless on a new phone. Re-linking is only
   possible if the same bytes exist there (see §4).
2. **Installation id copied by backup.** The id used to live in the backed-up DataStore, so Auto Backup
   and device transfer gave the new phone the old id. Its MediaStore URIs then looked local and could
   show an unrelated photo.
3. **Dependence on `READ_MEDIA_*`.** Converting picker URIs to MediaStore URIs made photos disappear
   when the permission was revoked or auto-reset for an unused app.
4. **Persisted-grant cap.** Android keeps at most 512 persisted grants per app and silently revokes
   the oldest. The app never released grants.
5. **Missing permission at re-link time** (fixed in 4ac20bd): MediaStore queries without permission return nothing.
6. **One unreadable candidate aborted the search** (fixed in 4ac20bd).
7. **Editing a memory with a missing file failed the save** (fixed in 4ac20bd).

---

## 3. Design

### URIs
* **Keep the photo picker URI** at save time. Its persisted grant survives reboots and does not depend
  on `READ_MEDIA_*`. Picker URIs are no longer converted to MediaStore URIs on save.
* **Re-linking to a MediaStore URI is the fallback** for items that are from another installation
  or no longer resolve. It needs media permission.
* **Release the grants of removed media.** When a save removes media from a memory, its grants are
  released unless another saved item still uses them (`LocalMediaUtil.releaseUriGrantsIfUnused`).
  This keeps the app under the 512-grant cap. There is deliberately no sweep of every unreferenced
  grant: after restoring an older backup, grants used only by a newer backup must survive.
  Grants for photos picked but never saved are not released.

### When an item needs re-linking
`deviceId != installationId` **or** `!isMediaValid(item)`.

`isMediaValid` runs a metadata query only (no file open, so cloud items are not downloaded).
The URI must be readable, and the file must be the same one: same size, **or** same capture date
(an in-place edit such as Samsung Gallery "Save" changes the size but keeps the date).
This catches a MediaStore id that now points at an unrelated photo.

### Matching rule
A MediaStore candidate is linked only if `candidate.size == item.fileSize` **and**
`signature(candidate) == item.mediaSignature`. There is no date-based rule:
* With a "reject on hash mismatch" safeguard, a size + date rule could only ever match candidates
  whose hash cannot be computed, which are files the app cannot read.
* Stored `dateTaken` can be synthetic (§1).

Re-linking updates only `uri` and `deviceId` (`MemoryGroupDao.updateMediaLink`), so a concurrent edit
of the same item (for example reordering) is not overwritten. The update applies only while the row still has the URI and
signature the run read, so a restore during the run (which can reuse ids) is not given another photo's link.

### Signatures
* Format: `{statSize}_{md5(first 4 KB + last 4 KB)}` (`MediaHasher`), computed on the redacted stream.
* Computed once, when media is first picked. Saving an existing item keeps its stored signature,
  because after a transfer its URI may resolve to a different file.

### Installation id
* Stored in `noBackupFilesDir/installation_identifier`, which Auto Backup and device transfer skip.
* Existing ids are migrated once from the DataStore key, which is then removed. Backups made by older
  versions still carry the id, so one more transfer from such a backup can still copy it. The
  `isMediaValid` size/date check covers that case.

### Permissions
* Re-linking runs with any media access, including Android 14+ "selected photos".
* The re-link buttons request full access when it is missing. After the dialog, re-linking runs if
  any access was granted.
* The cold-start Snackbar appears only when the app has no media access at all and media is missing.

### Diagnostics
"Send logs" adds `relink_diagnostics.txt` (`RelinkDiagnostics`, rendered by `RelinkReportFormatter`).
It is built when the user sends logs, not during re-link runs, so cold start pays nothing, and it only reads.
* **Installation:** the id, when its file was written, and the app's install and update times.
  An id file written at the last update, while items carry another device id, means that update gave
  the installation a new id.
* **Media access:** each media permission, the persisted grant count (cap 512) and how many photos and
  videos MediaStore shows the app.
* **Items:** counts by device id, and for every item that needs re-linking all of its reasons (another
  installation, URI unreadable with the error, or a different file), whether it still has a grant, and
  how many MediaStore files have its exact size and signature. This is what re-linking itself checks.
* **Nearby files** (first 40 items): up to 10 MediaStore files taken or changed within 24 hours of the stored
  date, trashed and pending ones included. Each shows its name, folder, type, dimensions, size and date
  deltas, owner package (for example Google Photos re-downloading a file) and signature.
* The report contains file names, folders and dates from the user's library.

---

## 4. Known limits
* **Different bytes on the new device are not re-linked.** Google Photos "Storage saver", re-encoding,
  EXIF rewrites or edits make the signature differ. Only exact copies (for example device transfer)
  can be matched.
* **Signatures depend on redaction state.** If the app ever gains `ACCESS_MEDIA_LOCATION`, or the
  photo picker's per-app "Include location info?" option is enabled, new reads would be unredacted
  and no longer match signatures stored from redacted reads. Not tested.
* **No backup schema changes.** Room stays at version 18; backups remain compatible.

---

## 5. Verification
1. `LocalMediaUtilTest` (JVM): the same-file rule. `RelinkReportTest` (JVM): the diagnostics reasons,
   nearby-file ordering and report text.
2. Emulator: pick photos, save, confirm picker URIs are stored and grants persist; revoke
   `READ_MEDIA_*` and confirm they still load; confirm cold start does not re-link valid picker items.
3. Note: when this plan was written, the Robolectric tests (`ColorUtilTest`) failed to start with
   `targetSdk 37`. They run with Robolectric 4.17, locally and in CI. This is unrelated to media re-linking.
