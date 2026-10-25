package com.szabolcshorvath.memorymap.backup

import android.content.Context
import java.io.File
import com.google.api.services.drive.model.File as DriveFile

/**
 * Where backup zips are kept for an account. The app uses [DriveBackupStorage]; UI tests replace it with a fake
 * (see [com.szabolcshorvath.memorymap.AppServices]). Every call blocks and throws on failure.
 */
interface BackupStorage {
    /** Every backup of [email], newest first. */
    fun list(context: Context, email: String): List<DriveFile>

    fun upload(context: Context, email: String, name: String, zipFile: File)

    fun download(context: Context, email: String, fileId: String, target: File)

    fun delete(context: Context, email: String, fileId: String)
}
