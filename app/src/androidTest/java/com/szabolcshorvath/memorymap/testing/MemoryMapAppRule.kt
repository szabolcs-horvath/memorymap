package com.szabolcshorvath.memorymap.testing

import android.graphics.Bitmap
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import androidx.test.uiautomator.By
import com.szabolcshorvath.memorymap.MainActivity
import com.szabolcshorvath.memorymap.screens.Ui
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Opens the app for a test and cleans up after it. Add test data with [TestData] first, then call [launch].
 * When a test fails, it saves a screenshot and the screen's element tree (`<Class>.<test>.png` and `.xml`), which
 * Gradle copies into the test results. Afterwards it turns the phone back upright and removes the photos the test
 * added to the gallery.
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
                saveScreen("${description.testClass.simpleName}.${description.methodName}")
                throw e
            } finally {
                cleanUp()
            }
        }
    }

    private fun saveScreen(name: String) {
        val storage = TestStorage()
        runCatching {
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            storage.openOutputFile("$name.png").use { screenshot.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        }.onFailure { Log.e(TAG, "Could not save the screenshot", it) }
        runCatching {
            storage.openOutputFile("$name.xml").use { Ui.device.dumpWindowHierarchy(it) }
        }.onFailure { Log.e(TAG, "Could not save the element tree", it) }
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
        const val PNG_QUALITY = 100 // Ignored for PNG, which is lossless.
    }
}
