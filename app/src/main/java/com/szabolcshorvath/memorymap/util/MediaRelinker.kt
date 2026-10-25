package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.util.Log
import com.szabolcshorvath.memorymap.MemoryMapApplication
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The one way to re-link media ([LocalMediaUtil.verifyAndFixMediaItems]). Runs happen one at a time in
 * [MemoryMapApplication.applicationScope], so they finish even if the screen that asked goes away, and
 * requests that arrive during a run share the next run (see [SerialRunner]).
 */
object MediaRelinker {
    const val TAG = "MediaRelinker"

    private val runner = SerialRunner<LocalMediaUtil.RelinkResult>()
    private val _progress = MutableStateFlow<String?>(null)

    /** The status message of the running re-link, or null when none is running. */
    val progress: StateFlow<String?> = _progress.asStateFlow()

    /**
     * Re-links media and returns the result. [onProgress] gets the status messages of the run, then its
     * summary, on the caller's dispatcher. Cancelling the caller stops only the waiting, not the run.
     */
    suspend fun relink(context: Context, onProgress: ((String) -> Unit)? = null): LocalMediaUtil.RelinkResult =
        coroutineScope {
            val app = context.applicationContext as MemoryMapApplication
            val progressJob = onProgress?.let { callback -> launch { progress.filterNotNull().collect(callback) } }
            val run = app.applicationScope.async {
                runner.run {
                    try {
                        val dao = MemoryMapDatabase.getDatabase(app).memoryGroupDao()
                        LocalMediaUtil.verifyAndFixMediaItems(app, dao) { _progress.value = it }
                    } finally {
                        _progress.value = null
                    }
                }
            }
            // Nobody may be waiting any more when the run fails, so log it here.
            run.invokeOnCompletion { e ->
                if (e != null && e !is CancellationException) Log.e(TAG, "Media re-linking failed", e)
            }
            try {
                run.await().also { onProgress?.invoke(it.summary) }
            } finally {
                progressJob?.cancel()
            }
        }
}
