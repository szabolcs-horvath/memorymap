package com.szabolcshorvath.memorymap.backup

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.szabolcshorvath.memorymap.auth.DriveAccessNeededException
import com.szabolcshorvath.memorymap.auth.DriveAuthorizationFailedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DriveProblemTest {

    @Test
    fun `missing consent or a token drive rejects needs the user, though both are IOExceptions`() {
        assertEquals(DriveProblem.NEEDS_ACCESS, DriveProblem.of(DriveAccessNeededException(consent = null)))
        assertEquals(DriveProblem.NEEDS_ACCESS, DriveProblem.of(driveError(401)))
    }

    @Test
    fun `no connection, a timeout or a dropped connection is a connection problem`() {
        listOf(UnknownHostException("www.googleapis.com"), SocketTimeoutException(), IOException("connection reset"))
            .forEach { assertEquals(DriveProblem.NO_CONNECTION, DriveProblem.of(it), it.toString()) }
    }

    @Test
    fun `drive server errors and rate limits mean drive is unavailable, other http errors are not`() {
        assertEquals(DriveProblem.DRIVE_UNAVAILABLE, DriveProblem.of(driveError(503)))
        assertEquals(DriveProblem.DRIVE_UNAVAILABLE, DriveProblem.of(driveError(429)))
        assertEquals(DriveProblem.OTHER, DriveProblem.of(driveError(403)))
        assertEquals(DriveProblem.OTHER, DriveProblem.of(driveError(404)))
    }

    @Test
    fun `a file that isn't there is no connection problem either`() {
        assertEquals(DriveProblem.OTHER, DriveProblem.of(FileNotFoundException("backup.zip")))
    }

    @Test
    fun `an authorization that failed for another reason is no connection problem`() {
        assertEquals(DriveProblem.OTHER, DriveProblem.of(DriveAuthorizationFailedException(IllegalStateException("developer error"))))
    }

    @Test
    fun `an error of the app itself is no drive problem the user can fix, and is not retried`() {
        val problem = DriveProblem.of(IllegalStateException("database copy is broken"))

        assertEquals(DriveProblem.OTHER, problem)
        assertFalse(problem.isRetryable)
    }

    @Test
    fun `only connection and server problems can work on their own when tried again`() {
        assertEquals(
            listOf(DriveProblem.NO_CONNECTION, DriveProblem.DRIVE_UNAVAILABLE),
            DriveProblem.entries.filter { it.isRetryable }
        )
    }

    private fun driveError(status: Int) =
        GoogleJsonResponseException(HttpResponseException.Builder(status, "error", HttpHeaders()), GoogleJsonError())
}
