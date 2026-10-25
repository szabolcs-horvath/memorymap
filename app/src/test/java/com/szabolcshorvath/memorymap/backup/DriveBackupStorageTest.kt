package com.szabolcshorvath.memorymap.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DriveBackupStorageTest {

    @Test
    fun `collects every page in order`() {
        val pages = mapOf<String?, Pair<List<Int>, String?>>(
            null to (listOf(1, 2) to "page2"),
            "page2" to (listOf(3, 4) to "page3"),
            "page3" to (listOf(5) to null)
        )
        val requested = mutableListOf<String?>()

        val all = DriveBackupStorage.collectAllPages { token ->
            requested += token
            pages.getValue(token)
        }

        assertEquals(listOf(1, 2, 3, 4, 5), all)
        assertEquals(listOf(null, "page2", "page3"), requested)
    }

    @Test
    fun `single page`() {
        assertEquals(listOf("a"), DriveBackupStorage.collectAllPages { listOf("a") to null })
    }

    @Test
    fun `blank next token ends the listing`() {
        assertEquals(listOf("a"), DriveBackupStorage.collectAllPages { listOf("a") to "" })
    }

    @Test
    fun `repeated next token ends the listing instead of looping forever`() {
        var calls = 0
        val all = DriveBackupStorage.collectAllPages {
            calls++
            listOf(calls) to "same"
        }
        assertEquals(listOf(1, 2), all)
    }

    @Test
    fun `empty listing`() {
        assertEquals(emptyList<Int>(), DriveBackupStorage.collectAllPages { emptyList<Int>() to null })
    }
}
