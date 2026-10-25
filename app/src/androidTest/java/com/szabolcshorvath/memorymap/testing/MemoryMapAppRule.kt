package com.szabolcshorvath.memorymap.testing

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import com.szabolcshorvath.memorymap.MainActivity
import com.szabolcshorvath.memorymap.screens.Ui
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream

/**
 * Opens the app for a test and cleans up after it. Add test data with [TestData] first, then call [launch].
 * When a test fails, it logs what was on screen (tag `UiTestScreen`; the test's logcat is kept with its results).
 * Afterwards it turns the phone back upright and removes the photos the test added to the gallery.
 */
class MemoryMapAppRule : TestRule {
    private var scenario: ActivityScenario<MainActivity>? = null

    fun launch(): ActivityScenario<MainActivity> {
        Ui.device.setOrientationNatural()
        return ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            // A busy emulator can show "System UI isn't responding" over the app. Lookups run watchers while they
            // wait, so this one taps "Wait" and the test carries on.
            Ui.device.registerWatcher(NOT_RESPONDING_WATCHER) {
                Ui.device.findObject(By.res("android", "aerr_wait"))?.click() != null
            }
            try {
                base.evaluate()
            } catch (e: Throwable) {
                logScreen()
                throw e
            } finally {
                cleanUp()
            }
        }
    }

    private fun logScreen() = runCatching {
        val hierarchy = ByteArrayOutputStream().also { Ui.device.dumpWindowHierarchy(it) }.toString()
        hierarchy.lines().forEach { Log.e(TAG, it) }
    }

    private fun cleanUp() {
        Ui.device.removeWatcher(NOT_RESPONDING_WATCHER)
        scenario?.close()
        runCatching {
            Ui.device.setOrientationNatural()
            Ui.device.unfreezeRotation()
        }
        TestData.deleteCreatedPhotos()
    }

    private companion object {
        const val TAG = "UiTestScreen"
        const val NOT_RESPONDING_WATCHER = "notResponding"
    }
}
