package com.szabolcshorvath.memorymap.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest

object MediaHasher {
    const val TAG = "MediaHasher"
    private const val ALGORITHM = "MD5"
    private const val BUFFER_SIZE = 4096 // 4KB

    fun calculateMediaSignature(context: Context, uri: Uri): String {
        val resolver = context.contentResolver

        // 1. Get the file size specifically from the FileDescriptor
        val size = resolver.openFileDescriptor(uri, "r")?.use {
            it.statSize
        } ?: throw FileNotFoundException("File not found: $uri")

        val digest = MessageDigest.getInstance(ALGORITHM)

        // 2. Open input stream using ContentResolver
        readFileAndUpdateDigest(resolver, uri, digest, size)

        // Combine Size + Hash for the final ID
        val hashString = digest.digest().joinToString("") { "%02x".format(it) }
        return "${size}_$hashString"
    }

    private fun readFileAndUpdateDigest(
        resolver: ContentResolver,
        uri: Uri,
        digest: MessageDigest,
        size: Long
    ) {
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
                handleLargeFile(size, bytesReadFirst, fis, buffer, digest)
            }
        } ?: throw FileNotFoundException("File not found: $uri")
    }

    private fun handleLargeFile(
        size: Long,
        bytesReadFirst: Int,
        fis: InputStream,
        buffer: ByteArray,
        digest: MessageDigest
    ) {
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
                error("Error skipping bytes")
            }
        }
    }
}
