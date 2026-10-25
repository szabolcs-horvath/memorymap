package com.szabolcshorvath.memorymap.backup

import android.content.Context
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
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Automatic backups run in the background through WorkManager, so they survive the screen (and the
 * process) that asked for them. Every change calls [schedule]; a burst of changes (reordering, several
 * saves) leads to a single upload [DEBOUNCE_SECONDS] after the last one, once there is a network.
 */
object AutomaticBackups {
    const val UNIQUE_WORK_NAME = "automatic_backup"

    private const val DEBOUNCE_SECONDS = 15L
    private const val RETRY_BACKOFF_MINUTES = 1L
    internal const val MAX_ATTEMPTS = 5

    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500

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
        .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
        .build()

    internal fun statusOf(states: Collection<WorkInfo.State>): AutomaticBackupStatus = when {
        WorkInfo.State.RUNNING in states -> AutomaticBackupStatus.RUNNING
        states.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED } -> AutomaticBackupStatus.PENDING
        WorkInfo.State.FAILED in states -> AutomaticBackupStatus.FAILED
        else -> AutomaticBackupStatus.IDLE
    }

    /**
     * Whether a failed backup is worth retrying later: network trouble and Drive server errors are,
     * missing consent or a rejected request are not (they would fail the same way again).
     */
    internal fun isRetryable(e: Throwable): Boolean = when (e) {
        is GoogleAuthIOException -> false // the user has to sign in or grant access again
        is GoogleJsonResponseException -> e.statusCode == HTTP_TOO_MANY_REQUESTS || e.statusCode >= HTTP_SERVER_ERROR
        is IOException -> true // no connection, timeout, connection reset
        else -> false
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
