package com.szabolcshorvath.memorymap.backup

import androidx.work.NetworkType
import androidx.work.WorkInfo.State
import com.google.android.gms.auth.GoogleAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

@ExtendWith(RobolectricExtension::class)
class AutomaticBackupsTest {

    @Test
    fun `request waits for changes to settle and for a network`() {
        val spec = AutomaticBackups.request().workSpec
        assertEquals(TimeUnit.SECONDS.toMillis(15), spec.initialDelay)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(AutomaticBackupWorker::class.java.name, spec.workerClassName)
    }

    @Test
    fun `a running backup wins over everything else`() {
        assertEquals(AutomaticBackupStatus.RUNNING, AutomaticBackups.statusOf(listOf(State.CANCELLED, State.RUNNING, State.ENQUEUED)))
    }

    @Test
    fun `waiting work is pending, also while it waits to retry`() {
        assertEquals(AutomaticBackupStatus.PENDING, AutomaticBackups.statusOf(listOf(State.ENQUEUED)))
        assertEquals(AutomaticBackupStatus.PENDING, AutomaticBackups.statusOf(listOf(State.BLOCKED)))
        assertEquals(AutomaticBackupStatus.PENDING, AutomaticBackups.statusOf(listOf(State.FAILED, State.ENQUEUED)))
    }

    @Test
    fun `a failure shows until the next backup is scheduled`() {
        assertEquals(AutomaticBackupStatus.FAILED, AutomaticBackups.statusOf(listOf(State.FAILED)))
    }

    @Test
    fun `finished, replaced or no work is idle`() {
        assertEquals(AutomaticBackupStatus.IDLE, AutomaticBackups.statusOf(emptyList()))
        assertEquals(AutomaticBackupStatus.IDLE, AutomaticBackups.statusOf(listOf(State.SUCCEEDED)))
        assertEquals(AutomaticBackupStatus.IDLE, AutomaticBackups.statusOf(listOf(State.CANCELLED)))
    }

    @Test
    fun `network trouble is retried`() {
        assertTrue(AutomaticBackups.isRetryable(IOException("connection reset")))
        assertTrue(AutomaticBackups.isRetryable(SocketTimeoutException()))
    }

    @Test
    fun `drive server errors and rate limits are retried, other http errors are not`() {
        assertTrue(AutomaticBackups.isRetryable(driveError(503)))
        assertTrue(AutomaticBackups.isRetryable(driveError(429)))
        assertFalse(AutomaticBackups.isRetryable(driveError(403)))
        assertFalse(AutomaticBackups.isRetryable(driveError(404)))
    }

    @Test
    fun `missing consent and local errors are not retried`() {
        assertFalse(AutomaticBackups.isRetryable(GoogleAuthIOException(GoogleAuthException("consent needed"))))
        assertFalse(AutomaticBackups.isRetryable(IllegalStateException("database copy is broken")))
    }

    private fun driveError(status: Int) =
        GoogleJsonResponseException(HttpResponseException.Builder(status, "error", HttpHeaders()), GoogleJsonError())
}
