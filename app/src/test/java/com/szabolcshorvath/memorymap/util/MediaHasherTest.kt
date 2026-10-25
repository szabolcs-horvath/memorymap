package com.szabolcshorvath.memorymap.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class MediaHasherTest {

    private fun bytes(count: Int) = ByteArray(count) { (it % 251).toByte() }

    /** Returns at most [maxRead] bytes per read and never skips, like a pipe-backed provider stream. */
    private class TrickleInputStream(data: ByteArray, private val maxRead: Int) : InputStream() {
        private val inner = ByteArrayInputStream(data)
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, minOf(len, maxRead))
        override fun skip(n: Long): Long = 0
    }

    // These values pin the stored signature format: existing signatures (in the database and in
    // backups) must keep matching.

    @Test
    fun `large file hashes the first and last 4 KB`() {
        assertEquals(
            "20000_c3ca6dd31eb34e9493a8985ce2b99328",
            MediaHasher.signatureOf(ByteArrayInputStream(bytes(20_000)), 20_000)
        )
    }

    @Test
    fun `small file hashes the whole file`() {
        assertEquals(
            "3000_a216503cb86d01a23e71e047d4ccf001",
            MediaHasher.signatureOf(ByteArrayInputStream(bytes(3_000)), 3_000)
        )
    }

    @Test
    fun `short reads and skips give the same signature`() {
        for (size in listOf(3_000, 8_192, 8_193, 20_000)) {
            val data = bytes(size)
            val expected = MediaHasher.signatureOf(ByteArrayInputStream(data), size.toLong())
            assertEquals(expected, MediaHasher.signatureOf(TrickleInputStream(data, maxRead = 100), size.toLong()), "size $size")
        }
    }

    @Test
    fun `seeking in a file gives the same signature`(@TempDir dir: File) {
        val data = bytes(20_000)
        val file = File(dir, "photo.jpg").apply { writeBytes(data) }
        FileInputStream(file).use {
            assertEquals("20000_c3ca6dd31eb34e9493a8985ce2b99328", MediaHasher.signatureOf(it, 20_000))
        }
    }

    @Test
    fun `unknown size hashes the whole stream`() {
        assertEquals(
            "-1_a216503cb86d01a23e71e047d4ccf001",
            MediaHasher.signatureOf(TrickleInputStream(bytes(3_000), maxRead = 7), -1)
        )
    }

    @Test
    fun `stream shorter than its size fails`() {
        assertThrows<IOException> {
            MediaHasher.signatureOf(TrickleInputStream(bytes(10_000), maxRead = 100), 20_000)
        }
    }
}
