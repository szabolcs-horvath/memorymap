package com.szabolcshorvath.memorymap.util

import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.util.MediaDetails.withDetailsOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaDetailsTest {

    @Test
    fun `a picture without edges hashes to zero, whatever its brightness`() {
        assertEquals(0L, MediaDetails.differenceHash(IntArray(72) { 40 }))
        assertEquals(0L, MediaDetails.differenceHash(IntArray(72) { 220 }))
    }

    @Test
    fun `brightness falling to the right sets every bit, and its mirror image none`() {
        val falling = grey { column, _ -> 255 - column * 20 }
        val rising = grey { column, _ -> column * 20 }

        assertEquals(-1L, MediaDetails.differenceHash(falling))
        assertEquals(0L, MediaDetails.differenceHash(rising))
        assertEquals(64, MediaDetails.hashDistance(MediaDetails.differenceHash(falling), MediaDetails.differenceHash(rising)))
    }

    @Test
    fun `a little noise moves the hash only a little`() {
        val photo = grey { column, row -> (column * 37 + row * 91) % 256 }
        val noisy = photo.mapIndexed { index, value -> if (index % 17 == 0) (value + 3).coerceAtMost(255) else value }.toIntArray()

        val distance = MediaDetails.hashDistance(MediaDetails.differenceHash(photo), MediaDetails.differenceHash(noisy))

        assertTrue(distance <= MediaDetails.LOOKS_ALIKE_DISTANCE, "distance $distance")
    }

    @Test
    fun `details missing from an item are taken from the same file saved before, known ones are kept`() {
        val saved = item().copy(displayName = "IMG_1.jpg", width = 4000, height = 3000, mimeType = "image/jpeg", perceptualHash = 7)
        val edited = item().copy(displayName = "renamed.jpg")

        val merged = edited.withDetailsOf(saved)

        assertEquals(saved.copy(displayName = "renamed.jpg"), merged)
    }

    private fun grey(value: (column: Int, row: Int) -> Int) = IntArray(72) { value(it % 9, it / 9) }

    private fun item() = MediaItem(
        id = 1,
        groupId = 1,
        uri = "content://media/picker/0/com.android.providers.media.photopicker/media/5",
        deviceId = "device",
        type = MediaType.IMAGE,
        mediaSignature = "100_abc",
        fileSize = 100,
        dateTaken = 1_788_023_205_927
    )
}
