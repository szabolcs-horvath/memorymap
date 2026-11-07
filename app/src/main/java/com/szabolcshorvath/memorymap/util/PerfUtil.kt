package com.szabolcshorvath.memorymap.util

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.google.firebase.Firebase
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.performance

object PerfUtil {
    fun startTrace(traceName: String): Trace {
        return Firebase.performance.newTrace(traceName).also { it.start() }
    }

    suspend fun <R> RoomDatabase.withTransaction(traceName: String, block: suspend () -> R): R {
        return trace(traceName + "_transaction") {
            this.withTransaction(block)
        }
    }

    /** Runs [block] in a Firebase Performance trace. Where Firebase isn't set up (unit tests), [block] runs untraced. */
    inline fun <E> trace(traceName: String, block: (Trace?) -> E): E {
        val trace = startedTraceOrNull(traceName)
        return try {
            block(trace)
        } finally {
            trace?.stop()
        }
    }

    /** A started trace, or null without a Firebase app: tracing must never stop the work it measures. */
    @PublishedApi
    internal fun startedTraceOrNull(traceName: String): Trace? = try {
        Firebase.performance.newTrace(traceName).also { it.start() }
    } catch (_: IllegalStateException) {
        null
    }
}
