package com.szabolcshorvath.memorymap.util

import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NearbyMediaTest {

    private val missing = item(id = 1, uri = "content://media/picker/0/com.android.providers.media.photopicker/media/5", signature = "100_lost")

    @Test
    fun `nearest come first, earlier or later, up to the limit`() {
        val files = listOf(file(1, offset = 120_000), file(2, offset = -7_000), file(3, offset = 30_000), file(4, offset = -240_000))

        val picked = NearbyMedia.pick(missing, files, listOf(missing), limit = 3) { "sig${it.takeLast(1)}" }

        assertEquals(listOf(2, 3, 1).map(::uri), picked.map { it.uri })
    }

    @Test
    fun `files already in the memory are left out, by URI or by signature`() {
        val inMemory = listOf(
            missing,
            item(id = 2, uri = uri(1), signature = "sig1"),
            item(id = 3, uri = "content://media/picker/0/x/media/9", signature = "sig2")
        )
        val files = listOf(file(1, offset = 1_000), file(2, offset = 2_000), file(3, offset = 3_000))

        val picked = NearbyMedia.pick(missing, files, inMemory, limit = 20) { "sig${it.takeLast(1)}" }

        assertEquals(listOf(uri(3)), picked.map { it.uri })
    }

    @Test
    fun `a file with the missing item's own signature is suggested, and one without a readable signature too`() {
        val files = listOf(file(1, offset = 1_000), file(2, offset = 2_000))
        val signatures = mapOf(uri(1) to "100_lost", uri(2) to null)

        val picked = NearbyMedia.pick(missing, files, listOf(missing), limit = 20) { signatures[it] }

        assertEquals(listOf(uri(1), uri(2)), picked.map { it.uri })
    }

    @Test
    fun `signatures are read only for the files that can make the list`() {
        val files = (1..10).map { file(it, offset = it * 1_000L) }
        val read = mutableListOf<String>()

        NearbyMedia.pick(missing, files, listOf(missing), limit = 2) {
            read += it
            "sig$it"
        }

        assertEquals(listOf(uri(1), uri(2)), read)
    }

    @Test
    fun `spans are rounded down to seconds, minutes or hours and minutes`() {
        assertEquals("0 s", NearbyMedia.span(400))
        assertEquals("7 s", NearbyMedia.span(-7_900))
        assertEquals("3 min", NearbyMedia.span(3 * 60_000L + 59_000))
        assertEquals("1 h", NearbyMedia.span(60 * 60_000L))
        assertEquals("1 h 5 min", NearbyMedia.span(65 * 60_000L))
    }

    private fun uri(n: Int) = "content://media/external/images/media/$n"

    private fun file(n: Int, offset: Long) = NearbyFile(uri(n), size = 2_000_000, dateTaken = TAKEN + offset)

    private fun item(id: Int, uri: String, signature: String) = MediaItem(
        id = id,
        groupId = 1,
        uri = uri,
        deviceId = "device",
        type = MediaType.IMAGE,
        mediaSignature = signature,
        fileSize = 100,
        dateTaken = TAKEN
    )

    private companion object {
        const val TAKEN = 1_788_023_205_927L
    }
}
