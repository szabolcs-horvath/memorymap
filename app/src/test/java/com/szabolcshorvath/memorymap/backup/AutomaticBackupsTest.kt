package com.szabolcshorvath.memorymap.backup

import androidx.work.NetworkType
import androidx.work.WorkInfo.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
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
}
