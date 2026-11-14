package com.szabolcshorvath.memorymap

import android.os.Looper
import androidx.room.Room
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import org.junit.jupiter.api.Assertions.fail
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val WAIT_TIMEOUT_MS = 10_000L
private const val POLL_MS = 10L

/**
 * An empty database in memory, for one test; close it afterwards. [mainThreadQueries]: a ViewModel's or model's code
 * that queries it from the main thread, which the test thread stands in for, may do so.
 */
fun inMemoryDatabase(mainThreadQueries: Boolean = false): MemoryMapDatabase =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MemoryMapDatabase::class.java)
        .apply { if (mainThreadQueries) allowMainThreadQueries() }
        .build()

/**
 * Waits until [condition] holds, for work that runs in the background. Each check first runs what was posted to the
 * main thread meanwhile, which under Robolectric runs only when the test thread lets it. Fails naming [what] if
 * [condition] still doesn't hold after [timeoutMs].
 */
fun awaitOnMain(what: String, timeoutMs: Long = WAIT_TIMEOUT_MS, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        if (System.currentTimeMillis() >= deadline) fail<Unit>("Waited for $what")
        Thread.sleep(POLL_MS)
    }
}
