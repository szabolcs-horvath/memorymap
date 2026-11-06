package com.szabolcshorvath.memorymap.backup

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Automatic backups run in the background through WorkManager, so they survive the screen (and the
 * process) that asked for them. Every change calls [schedule]; a burst of changes (reordering, several
 * saves) leads to a single upload [debounceSeconds] after the last one, once there is a network.
 */
object AutomaticBackups {
    const val UNIQUE_WORK_NAME = "automatic_backup"

    /** How long after the last change the backup starts. A UI test shortens it, so it doesn't wait 15 seconds. */
    @VisibleForTesting
    @Volatile
    internal var debounceSeconds = DEBOUNCE_SECONDS

    private const val DEBOUNCE_SECONDS = 15L
    private const val RETRY_BACKOFF_MINUTES = 1L
    internal const val MAX_ATTEMPTS = 5

    /**
     * Schedules an automatic backup. Replaces a pending one, which restarts the debounce delay, and also a
     * running one: its snapshot may already miss the newest change, and the replacement backs that up.
     * Does nothing when no user is signed in (checked when the backup runs).
     */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request())
    }

    /** The automatic backup's state, for showing it in the UI. */
    fun status(context: Context): Flow<AutomaticBackupStatus> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
            .map { infos -> statusOf(infos.map { it.state }) }
            .distinctUntilChanged()

    internal fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<AutomaticBackupWorker>()
        .setInitialDelay(debounceSeconds, TimeUnit.SECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
        .build()

    internal fun statusOf(states: Collection<WorkInfo.State>): AutomaticBackupStatus = when {
        WorkInfo.State.RUNNING in states -> AutomaticBackupStatus.RUNNING
        states.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED } -> AutomaticBackupStatus.PENDING
        WorkInfo.State.FAILED in states -> AutomaticBackupStatus.FAILED
        else -> AutomaticBackupStatus.IDLE
    }
}

enum class AutomaticBackupStatus { IDLE, PENDING, RUNNING, FAILED }

enum class AutomaticBackupOutcome { SUCCEEDED, NOT_SIGNED_IN, RETRY, FAILED }

class AutomaticBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = MemoryMapDatabase.getDatabase(applicationContext)
        return when (BackupManager(applicationContext).runAutomaticBackup(db)) {
            AutomaticBackupOutcome.SUCCEEDED, AutomaticBackupOutcome.NOT_SIGNED_IN -> Result.success()
            AutomaticBackupOutcome.RETRY -> if (runAttemptCount + 1 < AutomaticBackups.MAX_ATTEMPTS) Result.retry() else Result.failure()
            AutomaticBackupOutcome.FAILED -> Result.failure()
        }
    }
}
