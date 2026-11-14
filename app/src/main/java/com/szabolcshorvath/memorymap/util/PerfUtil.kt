package com.szabolcshorvath.memorymap.util

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.google.firebase.Firebase
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.performance

object PerfUtil {
    /**
     * Starts a trace named [traceName], which the caller stops. Null without a Firebase app (unit tests): tracing must
     * never stop the work it measures.
     */
    fun startTrace(traceName: String): Trace? = try {
        Firebase.performance.newTrace(traceName).also { it.start() }
    } catch (_: IllegalStateException) {
        null
    }

    suspend fun <R> RoomDatabase.withTransaction(traceName: String, block: suspend () -> R): R {
        return trace(traceName + "_transaction") {
            this.withTransaction(block)
        }
    }

    /** Runs [block] in a Firebase Performance trace. Where Firebase isn't set up (unit tests), [block] runs untraced. */
    inline fun <E> trace(traceName: String, block: () -> E): E {
        val trace = startTrace(traceName)
        return try {
            block()
        } finally {
            trace?.stop()
        }
    }
}
