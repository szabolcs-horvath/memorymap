package com.szabolcshorvath.memorymap.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs blocks one at a time and merges requests that pile up.
 *
 * A request made while a run is going waits for it and then starts a new run, because the running one may
 * have started before whatever prompted the request (a permission grant, a restore). Requests that wait
 * behind the same run share the next one instead of each starting their own.
 */
internal class SerialRunner<T> {
    private val mutex = Mutex()
    private val runsStarted = AtomicLong()

    /** The number and result of the last run that finished. Written only while holding [mutex]. */
    @Volatile
    private var lastRun: Pair<Long, T>? = null

    suspend fun run(block: suspend () -> T): T {
        val startedBefore = runsStarted.get()
        return mutex.withLock {
            val shared = lastRun?.takeIf { (number, _) -> number > startedBefore }
            if (shared != null) {
                shared.second
            } else {
                val number = runsStarted.incrementAndGet()
                block().also { lastRun = number to it }
            }
        }
    }
}
