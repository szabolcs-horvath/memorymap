# Media re-linking: evidence and reasons

How re-linking works today, and the rules to follow when changing it, are in [AGENTS.md](../AGENTS.md)
("Media handling and re-linking"). This document keeps what AGENTS.md doesn't: what the device logs and
emulator tests showed, and why the rules are what they are. Earlier theories (EXIF size shifts, expiring photo
picker URIs) did not hold up against this evidence.

## Evidence

### Real device (Samsung SM-A336B, Android 14)
* **Diagnostic build, 2026-10-06:** every logged item was a photo picker URI from this installation, and all 86
  re-linked by exact size + signature. Picker id `N` always matched MediaStore id `N`.
* **No size shift:** all 64 MediaStore candidates taken within ±15 s of an item had size delta 0, date delta 0
  and an identical signature. No candidate was a few hundred bytes off.
* **No URI access errors:** no log contains a permission denial, `SecurityException` or load failure for a
  media URI.
* **Affected user, after the re-linking release (reported 2026-10-25):** same phone, full media access, and no
  item re-linked. For one item found by date, the gallery reported about 4.6 MB where the app had stored about
  5.3 MB: too large a gap for units (MB vs MiB is under 5 %), so the file on the phone is not the bytes that were
  picked. The same user's diagnostics later showed one such cause: a photo cropped in the gallery after it was
  picked, under the same MediaStore id and date taken (REVIEW_FINDINGS BUG-20, now handled by `EditedPhotoRule`).

### Emulator (API 36, two JPEGs with GPS EXIF)
| Read path | Size | GPS bytes | Signature |
|---|---|---|---|
| Original file (unredacted) | 821,703 | intact | `821703_20d578…` |
| App, MediaStore URI | 821,703 | zeroed | `821703_6e2708…` |
| App, photo picker URI | 821,703 | zeroed | `821703_6e2708…` |

* **EXIF redaction is zero-fill in place.** The size never changes. Picker and MediaStore reads are redacted
  the same way (the app does not hold `ACCESS_MEDIA_LOCATION`), so signatures taken on one phone match.
* **Picker grants survive a reboot** (`takePersistableUriPermission`).
* **MediaStore URIs need the broad permission; picker URIs do not.** With `READ_MEDIA_*` revoked, picker URIs
  still opened and `content://media/external/...` URIs threw `SecurityException`.
* **The picker can report a made-up `DATE_TAKEN`.** For a photo without an EXIF date, the picker returned the
  file time while MediaStore returned 0.

### Database snapshot
* `content://media/external/images/media/38` was stored for two different photos (different signatures,
  different installations). MediaStore ids mean something only on the phone that made them.

## What broke re-linking, and the rule each led to
1. **Another phone.** URIs (picker or MediaStore) mean nothing on a new phone, so an item from another
   installation always needs re-linking, and only the same bytes can be found there.
2. **The installation id was copied by backups.** It lived in the backed-up DataStore, so Auto Backup and device
   transfer gave the new phone the old id, its MediaStore URIs looked local and could show an unrelated photo.
   The id now lives in `noBackupFilesDir`. Backups made by older versions still carry it in the DataStore, which
   is why `isMediaValid` also checks that a readable URI is still the same file (size, or date for an in-place
   edit).
3. **Depending on `READ_MEDIA_*`.** Converting picker URIs to MediaStore URIs made photos disappear when the
   permission was revoked or auto-reset. The picker URI is kept; a MediaStore URI is only the re-link fallback.
4. **The persisted-grant cap.** Android keeps at most 512 grants per app and silently revokes the oldest, and the
   app never released any. Grants are now released when media is removed or re-linked, but never swept: after
   restoring an older backup, a newer backup may still need grants the current database doesn't use.

## Why the matching rules are strict
* **No date-based match on its own.** Stored `dateTaken` can be made up by the picker (above), and dates with
  whole seconds only (videos, messenger images) collide for shots taken in the same second. With a "reject on
  signature mismatch" safeguard, a size + date rule could only ever match files whose signature can't be read.
  The one date-based rule, `EditedPhotoRule`, also needs the MediaStore id from the item's own picker URI, the
  same type and the date to the millisecond. A wrong link is worse than a missing one: the missing-photo sheet
  leaves the other cases to the user.
* **Signatures depend on redaction.** If the app ever holds `ACCESS_MEDIA_LOCATION`, or the picker's "Include
  location info?" option is turned on, new reads would be unredacted and no longer match signatures stored from
  redacted reads. Not tested.

## Known limits
* **Different bytes on another phone are not re-linked.** Google Photos "Storage saver", re-encoding, EXIF
  rewrites and edits change the signature, so only exact copies (device transfer, for example) are found. The
  stored details (name, pixel size, perceptual hash, thumbnail) help the user find such a photo in the
  missing-photo sheet, but are never used to link on their own. Whether the files' own unique ids (EXIF
  `ImageUniqueID`, XMP document ids) could be matched is being measured in the "Send logs" diagnostics.
