package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.EOFException
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

object MediaHasher {
    const val TAG = "MediaHasher"
    private const val ALGORITHM = "MD5"
    private const val BLOCK_SIZE = 4096 // 4KB

    /**
     * Returns `"{size}_{md5}"`. The MD5 covers the first and the last 4 KB of the file, or the whole file
     * if it is at most 8 KB or its size is unknown (-1). Signatures are stored in the database and in
     * backups, so this format must never change.
     */
    fun calculateMediaSignature(context: Context, uri: Uri): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("File not found: $uri")
        val size = descriptor.statSize
        // Closing the stream also closes the descriptor.
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { signatureOf(it, size) }
    }

    /**
     * The signature of [input], whose length is [size]. Reads fill their buffer even when the stream
     * returns fewer bytes per call (pipes, cloud providers), so the same file always gets the same signature.
     */
    internal fun signatureOf(input: InputStream, size: Long): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BLOCK_SIZE)
        if (size <= 2L * BLOCK_SIZE) {
            while (true) {
                val read = readFully(input, buffer)
                if (read == 0) break
                digest.update(buffer, 0, read)
            }
        } else {
            val headRead = readFully(input, buffer)
            digest.update(buffer, 0, headRead)
            moveTo(input, headRead.toLong(), size - BLOCK_SIZE)
            val tailRead = readFully(input, buffer)
            // A seek past the end succeeds, so check here too that the file is as long as its size says.
            if (tailRead < BLOCK_SIZE) throw EOFException("Stream ended ${BLOCK_SIZE - tailRead} bytes early")
            digest.update(buffer, 0, tailRead)
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return "${size}_$hash"
    }

    /** Reads until [buffer] is full or the stream ends. Returns the number of bytes read. */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    /** Moves the stream from [current] to [target]: a seek where possible, otherwise skipping or reading. */
    private fun moveTo(input: InputStream, current: Long, target: Long) {
        if (input is FileInputStream && trySeek(input, target)) return
        var remaining = target - current
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            when {
                skipped > 0 -> remaining -= skipped
                input.read() != -1 -> remaining--
                else -> throw EOFException("Stream ended $remaining bytes before the last block")
            }
        }
    }

    /** Pipes can't seek; the caller then skips instead. */
    private fun trySeek(input: FileInputStream, target: Long): Boolean = try {
        input.channel.position(target)
        true
    } catch (_: IOException) {
        false
    }
}
