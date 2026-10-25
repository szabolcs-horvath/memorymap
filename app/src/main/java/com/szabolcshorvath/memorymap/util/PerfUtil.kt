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

    inline fun <E> trace(traceName: String, block: (Trace) -> E): E {
        val trace = Firebase.performance.newTrace(traceName).also { it.start() }
        return try {
            block(trace)
        } finally {
            trace.stop()
        }
    }
}
