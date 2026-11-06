package com.szabolcshorvath.memorymap.backup

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.szabolcshorvath.memorymap.auth.DriveAccessNeededException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DriveBackupStorageTest {

    @Test
    fun `collects every page in order`() {
        val pages = mapOf(
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

    @Test
    fun `a call that works uses the first token and clears nothing`() {
        val cleared = mutableListOf<String>()
        val tokens = ArrayDeque(listOf("t1", "t2"))

        val result = DriveBackupStorage.withFreshToken({ tokens.removeFirst() }, { cleared += it }) { token -> "listed with $token" }

        assertEquals("listed with t1", result)
        assertEquals(emptyList<String>(), cleared)
    }

    @Test
    fun `a rejected token is cleared and the call tried once more with a new one`() {
        val cleared = mutableListOf<String>()
        val tokens = ArrayDeque(listOf("stale", "fresh"))

        val result = DriveBackupStorage.withFreshToken({ tokens.removeFirst() }, { cleared += it }) { token ->
            if (token == "stale") throw driveError(401) else "listed with $token"
        }

        assertEquals("listed with fresh", result)
        assertEquals(listOf("stale"), cleared)
    }

    @Test
    fun `a token rejected twice means the user has to allow access`() {
        val cleared = mutableListOf<String>()
        val tokens = ArrayDeque(listOf("t1", "t2"))

        assertThrows<DriveAccessNeededException> {
            DriveBackupStorage.withFreshToken({ tokens.removeFirst() }, { cleared += it }) { throw driveError(401) }
        }
        assertEquals(listOf("t1", "t2"), cleared)
    }

    @Test
    fun `other drive errors are not retried`() {
        var calls = 0

        val error = assertThrows<GoogleJsonResponseException> {
            DriveBackupStorage.withFreshToken({ "t" }, {}) {
                calls++
                throw driveError(503)
            }
        }
        assertEquals(503, error.statusCode)
        assertEquals(1, calls)
    }

    private fun driveError(status: Int) =
        GoogleJsonResponseException(HttpResponseException.Builder(status, "error", HttpHeaders()), GoogleJsonError())
}
