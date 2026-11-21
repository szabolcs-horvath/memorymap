package com.szabolcshorvath.memorymap.backup

import android.content.Context
import android.util.Log
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import java.time.Instant
import java.time.ZoneOffset
import com.google.api.services.drive.model.File as DriveFile

/**
 * Which automatic backups to move to Drive's trash, for the user's [BackupRetention]. Manual backups are never trimmed,
 * nor the newest [KEPT_AUTOMATIC_BACKUPS] automatic ones, however old: automatic backups are made only after a change,
 * so months without one would otherwise leave none at all. A backup's age is Drive's creation time; one without it is
 * kept.
 */
object BackupTrimming {
    const val KEPT_AUTOMATIC_BACKUPS = 3
    private const val TAG = "BackupTrimming"

    /**
     * Moves [email]'s automatic backups that [retention] no longer keeps to Drive's trash, after a new one was uploaded
     * (while the backup lock is held, so no restore downloads one meanwhile). A failure is only logged: the backup itself
     * succeeded, and the next one tries again.
     */
    fun trim(context: Context, email: String, retention: BackupRetention) {
        if (retention == BackupRetention.NEVER) return
        trace("backup_trimming_trim") {
            try {
                val storage = AppServices.backupStorage
                toTrash(storage.list(context, email), retention, Instant.now()).forEach { backup ->
                    storage.trash(context, email, backup.id)
                    Log.i(TAG, "Moved the old automatic backup ${backup.name} to Drive's trash")
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "Couldn't trim the old automatic backups", e)
            }
        }
    }

    /** Of [backups], the automatic ones older than [retention] allows at [now], beyond the newest few. */
    fun toTrash(backups: List<DriveFile>, retention: BackupRetention, now: Instant): List<DriveFile> {
        val months = retention.months ?: return emptyList()
        val cutoff = now.atZone(ZoneOffset.UTC).minusMonths(months).toInstant()
        // Undated ones are left out: they are kept, and don't count towards the newest few either.
        return backups
            .filter { it.name.orEmpty().startsWith(BackupManager.AUTOMATIC_BACKUP_PREFIX) && it.createdTime != null }
            .sortedByDescending { it.createdTime.value }
            .drop(KEPT_AUTOMATIC_BACKUPS)
            .filter { Instant.ofEpochMilli(it.createdTime.value) < cutoff }
    }
}
