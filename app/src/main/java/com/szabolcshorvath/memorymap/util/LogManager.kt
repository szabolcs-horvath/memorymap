package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.szabolcshorvath.memorymap.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogManager {
    private const val TAG = "LogManager"
    private const val LOG_FILENAME = "logcat.txt"
    private const val INFO_FILENAME = "app_info.txt"
    private const val ZIP_FILENAME = "debug_logs.zip"
    private const val BYTE_IN_KILOBYTE = 1024

    suspend fun sendDebugLogs(context: Context, lastBackupDate: String?) {
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                val logFile = File(cacheDir, LOG_FILENAME)
                val infoFile = File(cacheDir, INFO_FILENAME)
                val zipFile = File(cacheDir, ZIP_FILENAME)

                captureLogcat(logFile)
                captureAppInfo(context, infoFile, lastBackupDate)
                createZip(zipFile, listOf(logFile, infoFile))

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_SUBJECT, "Memory Map Debug Logs")
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Send Debug Logs")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare debug logs", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to prepare debug logs: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun captureLogcat(outputFile: File) {
        try {
            val process = Runtime.getRuntime().exec("/system/bin/logcat -d")
            val reader = InputStreamReader(process.inputStream)
            outputFile.writeText(reader.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture logcat", e)
            outputFile.writeText("Failed to capture logcat: ${e.message}")
        }
    }

    private suspend fun captureAppInfo(context: Context, outputFile: File, lastBackupDate: String?) {
        val dbFile = context.getDatabasePath("memory_map_database")
        val dbSize = if (dbFile.exists()) dbFile.length() / BYTE_IN_KILOBYTE else 0
        val installationId = InstallationIdentifier.getInstallationIdentifier(context)

        val info = StringBuilder().apply {
            append("--- App Info ---\n")
            append("Version Name: ${BuildConfig.VERSION_NAME}\n")
            append("Version Code: ${BuildConfig.VERSION_CODE}\n")
            append("Installation ID: $installationId\n")
            append("\n")
            append("--- Device Info ---\n")
            append("Manufacturer: ${Build.MANUFACTURER}\n")
            append("Model: ${Build.MODEL}\n")
            append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("Display Density: ${context.resources.displayMetrics.densityDpi} dpi\n")
            append("\n")
            append("--- DB Stats ---\n")
            append("Database Size: $dbSize KB\n")
            append("\n")
            append("--- Backup Info ---\n")
            append("Last Successful Backup: ${lastBackupDate ?: "None found in current session"}\n")
        }.toString()

        outputFile.writeText(info)
    }

    private fun createZip(zipFile: File, files: List<File>) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            for (file in files) {
                if (file.exists()) {
                    val entry = ZipEntry(file.name)
                    out.putNextEntry(entry)
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
            }
        }
    }
}
