package com.szabolcshorvath.memorymap.backup

import com.google.api.client.util.DateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import com.google.api.services.drive.model.File as DriveFile

class BackupTrimmingTest {

    @Test
    fun `nothing is trimmed when the user never chose to`() {
        val backups = (1..10).map { automatic(daysAgo = it * 100L) }

        assertEquals(emptyList<DriveFile>(), BackupTrimming.toTrash(backups, BackupRetention.NEVER, NOW))
    }

    @Test
    fun `automatic backups older than the retention are trimmed, beyond the newest three`() {
        val recent = automatic(daysAgo = 2)
        val old = (1..5).map { automatic(daysAgo = 40L + it) }

        val trashed = BackupTrimming.toTrash(listOf(recent) + old, BackupRetention.ONE_MONTH, NOW)

        // The newest three are the recent one and the two newest old ones.
        assertEquals(old.drop(2).names(), trashed.names())
    }

    @Test
    fun `the newest three automatic backups are kept however old they are`() {
        val backups = (1..3).map { automatic(daysAgo = 400L + it) }

        assertEquals(emptyList<DriveFile>(), BackupTrimming.toTrash(backups, BackupRetention.ONE_MONTH, NOW))
    }

    @Test
    fun `manual backups are never trimmed`() {
        val manual = (1..5).map { manual(daysAgo = 1000L + it) }
        val automatic = (1..3).map { automatic(daysAgo = it.toLong()) }

        assertEquals(emptyList<DriveFile>(), BackupTrimming.toTrash(manual + automatic, BackupRetention.ONE_MONTH, NOW))
    }

    @Test
    fun `backups within the retention are kept`() {
        val backups = (1..10).map { automatic(daysAgo = it * 8L) } // 8 to 80 days old

        val trashed = BackupTrimming.toTrash(backups, BackupRetention.THREE_MONTHS, NOW)

        assertEquals(emptyList<DriveFile>(), trashed)
    }

    @Test
    fun `a backup without a creation time is kept and doesn't count towards the newest three`() {
        val undated = DriveFile().setId("undated").setName("${BackupManager.AUTOMATIC_BACKUP_PREFIX}20240101_120000.zip")
        val old = (1..4).map { automatic(daysAgo = 400L + it) }

        val trashed = BackupTrimming.toTrash(listOf(undated) + old, BackupRetention.ONE_YEAR, NOW)

        assertEquals(old.drop(3).names(), trashed.names())
    }

    private fun automatic(daysAgo: Long) = backup(BackupManager.AUTOMATIC_BACKUP_PREFIX, daysAgo)

    private fun manual(daysAgo: Long) = backup(BackupManager.MANUAL_BACKUP_PREFIX, daysAgo)

    private fun backup(prefix: String, daysAgo: Long) = DriveFile()
        .setId("$prefix$daysAgo")
        .setName("$prefix$daysAgo.zip")
        .setCreatedTime(DateTime(NOW.minus(Duration.ofDays(daysAgo)).toEpochMilli()))

    private fun List<DriveFile>.names() = map { it.name }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-21T12:00:00Z")
    }
}
