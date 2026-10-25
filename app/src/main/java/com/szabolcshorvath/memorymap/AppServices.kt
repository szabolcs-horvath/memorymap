package com.szabolcshorvath.memorymap

import androidx.annotation.VisibleForTesting
import com.szabolcshorvath.memorymap.auth.AccountService
import com.szabolcshorvath.memorymap.auth.GoogleAccountService
import com.szabolcshorvath.memorymap.backup.BackupStorage
import com.szabolcshorvath.memorymap.backup.DriveBackupStorage

/**
 * The outside services the app talks to through an interface. UI tests replace them with fakes before the app
 * starts, so a test never signs in to a real account or touches real backups. Always read them here, at the
 * time of the call, instead of keeping a reference.
 */
object AppServices {
    @Volatile
    @set:VisibleForTesting
    var accounts: AccountService = GoogleAccountService

    @Volatile
    @set:VisibleForTesting
    var backupStorage: BackupStorage = DriveBackupStorage
}
