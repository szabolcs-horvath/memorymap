package com.szabolcshorvath.memorymap.util

import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.util.LocalMediaUtil.MediaMetadata
import org.junit.jupiter.api.Assertions.assertEquals
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

    // --- findDuplicateMedia ---

    @Test
    fun `duplicates within a memory keep the oldest item`() {
        val media = listOf(
            media(id = 5, groupId = 1, signature = "a"),
            media(id = 2, groupId = 1, signature = "a"),
            media(id = 9, groupId = 1, signature = "a")
        )
        assertEquals(listOf(5, 9), LocalMediaUtil.findDuplicateMedia(media).map { it.id })
    }

    @Test
    fun `the same photo in different memories is not a duplicate`() {
        val media = listOf(media(id = 1, groupId = 1, signature = "a"), media(id = 2, groupId = 2, signature = "a"))
        assertEquals(emptyList<MediaItem>(), LocalMediaUtil.findDuplicateMedia(media))
    }

    @Test
    fun `items without a signature are never duplicates`() {
        val media = listOf(media(id = 1, groupId = 1, signature = ""), media(id = 2, groupId = 1, signature = ""))
        assertEquals(emptyList<MediaItem>(), LocalMediaUtil.findDuplicateMedia(media))
    }

    // --- closeOrderGaps ---

    @Test
    fun `gaps are closed keeping the current order`() {
        val remaining = listOf(media(id = 10, order = 4), media(id = 11, order = 1), media(id = 12, order = 3))
        // 1, 3, 4 -> 1, 2, 3: only the items whose order changes are returned.
        assertEquals(mapOf(12 to 2, 10 to 3), LocalMediaUtil.closeOrderGaps(remaining))
    }

    @Test
    fun `no gaps means no changes`() {
        assertEquals(emptyMap<Int, Int>(), LocalMediaUtil.closeOrderGaps(listOf(media(id = 1, order = 1), media(id = 2, order = 2))))
    }

    @Test
    fun `a memory with unordered items is left alone`() {
        val remaining = listOf(media(id = 1, order = 1), media(id = 2, order = null), media(id = 3, order = 5))
        assertEquals(emptyMap<Int, Int>(), LocalMediaUtil.closeOrderGaps(remaining))
    }

    private fun media(id: Int, groupId: Int = 1, signature: String = "sig$id", order: Int? = null) = MediaItem(
        id = id,
        groupId = groupId,
        uri = "content://media/$id",
        deviceId = "device",
        type = MediaType.IMAGE,
        mediaSignature = signature,
        fileSize = 1024,
        dateTaken = 0,
        order = order
    )
}
