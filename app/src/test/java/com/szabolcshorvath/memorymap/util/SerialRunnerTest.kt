package com.szabolcshorvath.memorymap.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SerialRunnerTest {

    @Test
    fun `requests made one after another each run`() = runBlocking {
        val runner = SerialRunner<Int>()
        var runs = 0
        assertEquals(1, runner.run { ++runs })
        assertEquals(2, runner.run { ++runs })
    }

    @Test
    fun `requests made during a run wait and then share one new run`() = runBlocking {
        val runner = SerialRunner<Int>()
        var runs = 0
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async {
            runner.run {
                firstStarted.complete(Unit)
                releaseFirst.await()
                ++runs
            }
        }
        firstStarted.await()
        // Both arrive while the first run is going, which may have started before what they need.
        val second = async { runner.run { ++runs } }
        val third = async { runner.run { ++runs } }
        yield()
        releaseFirst.complete(Unit)

        assertEquals(1, first.await())
        assertEquals(2, second.await())
        assertEquals(2, third.await())
        assertEquals(2, runs)
    }

    @Test
    fun `a failed run is not shared`() = runBlocking {
        val runner = SerialRunner<Int>()
        val failure = runCatching { runner.run { error("no media access") } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(7, runner.run { 7 })
    }
}
