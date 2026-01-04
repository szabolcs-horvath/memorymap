package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.security.MessageDigest

object MediaHasher {
    private const val TAG = "MediaHasher"
    private const val ALGORITHM = "MD5"
    private const val BUFFER_SIZE = 4096 // 4KB

    fun calculateMediaSignature(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver

        return try {
            // 1. Get the file size specifically from the FileDescriptor
            val size = resolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: return null

            val digest = MessageDigest.getInstance(ALGORITHM)

            // 2. Open input stream using ContentResolver
            resolver.openInputStream(uri)?.use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)

                // Read first 4KB
                val bytesReadFirst = fis.read(buffer)
                if (bytesReadFirst > 0) {
                    digest.update(buffer, 0, bytesReadFirst)
                }

                // If file is smaller than or equal to 8KB (2x BUFFER),
                // we just read the rest sequentially to avoid skip logic errors on small files.
                // Otherwise, we skip to the end.
                if (size <= (BUFFER_SIZE * 2)) {
                    // Read whatever is left normally
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                } else {
                    // Large file logic: Skip to the last 4KB
                    val remaining = size - bytesReadFirst
                    val skipAmount = remaining - BUFFER_SIZE

                    if (skipAmount > 0) {
                        val skipped = fis.skip(skipAmount)
                        // Verify skip success before reading final block
                        if (skipped == skipAmount) {
                            val bytesReadLast = fis.read(buffer)
                            if (bytesReadLast > 0) {
                                digest.update(buffer, 0, bytesReadLast)
                            }
                        } else {
                            Log.e(TAG, "Error skipping bytes: $skipped != $skipAmount")
                            return null
                        }
                    }
                }
            } ?: return null

            // Combine Size + Hash for the final ID
            val hashString = digest.digest().joinToString("") { "%02x".format(it) }
            "${size}_$hashString"

        } catch (e: Exception) {
            // Handle FileNotFoundException or SecurityException
            Log.e(TAG, "Error calculating media signature", e)
            null
        }
    }
}