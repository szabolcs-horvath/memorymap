package com.szabolcshorvath.memorymap.util

import com.szabolcshorvath.memorymap.util.LocalMediaUtil.MediaMetadata
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalMediaUtilTest {

    @Test
    fun `same size is the same file`() {
        assertTrue(LocalMediaUtil.isSameFile(3_398_742, 1_788_023_205_927, MediaMetadata(3_398_742, 0)))
    }

    @Test
    fun `in-place edit keeps the capture date and still counts as the same file`() {
        assertTrue(LocalMediaUtil.isSameFile(3_398_742, 1_788_023_205_927, MediaMetadata(3_512_004, 1_788_023_205_927)))
    }

    @Test
    fun `different size and date is a different file`() {
        // A MediaStore id from another phone resolving to an unrelated photo.
        assertFalse(LocalMediaUtil.isSameFile(144_694, 1_770_480_844_555, MediaMetadata(142_686, 1_766_945_740_062)))
    }

    @Test
    fun `different size with no stored date is a different file`() {
        assertFalse(LocalMediaUtil.isSameFile(144_694, 0, MediaMetadata(142_686, 0)))
    }

    @Test
    fun `unknown sizes are not treated as a mismatch`() {
        assertTrue(LocalMediaUtil.isSameFile(0, 0, MediaMetadata(142_686, 0)))
        assertTrue(LocalMediaUtil.isSameFile(144_694, 0, MediaMetadata(0, 0)))
    }
}
