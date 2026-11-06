package com.szabolcshorvath.memorymap.testing

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_LAUNCHER
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import androidx.test.uiautomator.By
import com.szabolcshorvath.memorymap.MainActivity
import com.szabolcshorvath.memorymap.screens.Ui
import org.junit.Assert.assertTrue
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
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val activities = ActivityTracker()

    private var isDarkModeOn = false

    /** Turns on dark mode for the whole phone (before [launch]); the rule turns it off again after the test. */
    fun useDarkMode() {
        isDarkModeOn = true
        Ui.device.executeShellCommand("cmd uimode night yes")
    }

    private var animationScaleBefore: String? = null

    /**
     * Turns animations on for this test: the test phones run with them off (`scripts/ui-tests.sh` turns them off on
     * the test emulator, `animationsDisabled` on the managed ones), so an animation such as the timeline's flash would
     * end at once. A [slowdown] above 1 makes them last that many times longer, so a slow phone can't miss a short
     * one. The rule puts the old setting back after the test, or removes it if it wasn't set ("null").
     */
    fun useAnimations(slowdown: Int = 1) {
        animationScaleBefore = Ui.device.executeShellCommand("settings get global $ANIMATOR_SCALE").trim()
        Ui.device.executeShellCommand("settings put global $ANIMATOR_SCALE $slowdown")
    }

    private var interactiveTimeoutBefore: String? = null

    /**
     * Sets Android's accessibility setting "Time to take action" to [seconds], as a user who needs longer does. A
     * message with an action, such as "Memory deleted" with Undo, then stays at least that long, so a slow phone
     * doesn't close it before the test taps its action. The rule puts the old setting back after the test.
     */
    fun allowTimeToTakeAction(seconds: Int) {
        interactiveTimeoutBefore = Ui.device.executeShellCommand("settings get secure $INTERACTIVE_TIMEOUT").trim()
        Ui.device.executeShellCommand("settings put secure $INTERACTIVE_TIMEOUT ${seconds * MS_PER_SECOND}")
    }

    private var clockBefore: String? = null

    /**
     * Sets the phone's clock to 12 or 24 [hours], as the "Use 24-hour format" switch in Android's date and time
     * settings does. The app reads it whenever it shows a time, so a test can switch it while the app is open. The
     * rule puts back the setting from before the first call after the test, or removes it if it wasn't set, so the
     * language decides again.
     */
    fun useClock(hours: Int) {
        if (clockBefore == null) clockBefore = Ui.device.executeShellCommand("settings get system $TIME_12_24").trim()
        Ui.device.executeShellCommand("settings put system $TIME_12_24 $hours")
    }

    /** Puts a setting back to [before], as `settings get` printed it: "null" when it wasn't set. */
    private fun restoreSetting(namespace: String, key: String, before: String) {
        if (before.isEmpty() || before == "null") {
            Ui.device.executeShellCommand("settings delete $namespace $key")
        } else {
            Ui.device.executeShellCommand("settings put $namespace $key $before")
        }
    }

    private var isOffline = false

    /**
     * Turns off Wi-Fi and mobile data and waits until the phone has no network. The rule turns them on again after the
     * test and waits until the network works, so the next test (and its map) starts online.
     */
    fun goOffline() {
        isOffline = true
        Ui.device.executeShellCommand("svc wifi disable")
        Ui.device.executeShellCommand("svc data disable")
        assertTrue("The phone still has a network", Ui.waitUntil { !hasWorkingNetwork() })
    }

    private fun hasWorkingNetwork(): Boolean {
        val connectivity = application.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Starts the app the way its launcher icon does, in a new task, and waits until it is open. */
    fun launch() {
        Ui.device.setOrientationNatural()
        startFromLauncher(clearTask = true)
        assertTrue("The app did not open", Ui.waitUntil { activities.isMainActivityResumed })
    }

    /**
     * Has Android throw away the app's screens as it does when it kills the app in the background, then returns to
     * the app the way a user does. Android saves each screen's state and clears its ViewModels, then rebuilds the
     * screens from the saved state alone, so a screen that keeps something only in its ViewModel loses it, as after
     * process death. The process itself keeps running: the database, the fakes and other app-wide objects stay.
     */
    fun simulateProcessDeath() {
        setDontKeepActivities(true)
        try {
            Ui.device.pressHome()
            assertTrue("Android did not close the app's screens in the background", Ui.waitUntil { activities.noneAlive })
            activities.forgetLastCreated()
            startFromLauncher(clearTask = false)
            assertTrue("The app did not come back", Ui.waitUntil { activities.isMainActivityResumed })
            assertTrue("The app started over instead of restoring its screens", activities.lastCreatedFromSavedState)
        } finally {
            setDontKeepActivities(false)
        }
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            // A busy emulator can show "System UI isn't responding" over the app. Lookups run watchers while they
            // wait, so this one taps "Wait" and the test carries on.
            Ui.device.registerWatcher(NOT_RESPONDING_WATCHER) {
                Ui.device.findObject(By.res("android", "aerr_wait"))?.click() != null
            }
            application.registerActivityLifecycleCallbacks(activities)
            // The setting outlives the app: a test that crashed in the middle may have left it on.
            setDontKeepActivities(false)
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

    private fun startFromLauncher(clearTask: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = "${context.packageName}/${MainActivity::class.java.name}"
        // The same intent as the launcher's, so a second start brings back the existing task instead of a new one.
        val clear = if (clearTask) "--activity-clear-task" else ""
        Ui.device.executeShellCommand("am start -W $clear -a $ACTION_MAIN -c $CATEGORY_LAUNCHER -n $component")
    }

    /**
     * Turns "Don't keep activities" (Developer options) on or off. Writing the setting alone takes effect only after a
     * reboot, so this makes the same system call as Developer options, with the shell's permission to make it.
     */
    private fun setDontKeepActivities(on: Boolean) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(SET_ALWAYS_FINISH)
        try {
            val activityManager = ActivityManager::class.java.getMethod("getService").invoke(null)
            activityManager.javaClass
                .getMethod("setAlwaysFinish", Boolean::class.javaPrimitiveType)
                .invoke(activityManager, on)
        } finally {
            automation.dropShellPermissionIdentity()
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
        runCatching { setDontKeepActivities(false) }
        application.unregisterActivityLifecycleCallbacks(activities)
        runCatching {
            Ui.device.setOrientationNatural()
            Ui.device.unfreezeRotation()
        }
        if (isDarkModeOn) runCatching { Ui.device.executeShellCommand("cmd uimode night no") }
        animationScaleBefore?.let { before -> runCatching { restoreSetting("global", ANIMATOR_SCALE, before) } }
        interactiveTimeoutBefore?.let { before -> runCatching { restoreSetting("secure", INTERACTIVE_TIMEOUT, before) } }
        clockBefore?.let { before -> runCatching { restoreSetting("system", TIME_12_24, before) } }
        if (isOffline) {
            runCatching {
                Ui.device.executeShellCommand("svc wifi enable")
                Ui.device.executeShellCommand("svc data enable")
            }
            if (!Ui.waitUntil(NETWORK_TIMEOUT_MS) { hasWorkingNetwork() }) Log.w(TAG, "The network did not come back")
        }
        TestData.deleteCreatedPhotos()
    }

    /** Follows the app's activities: which are alive, and whether the last one was rebuilt from saved state. */
    private class ActivityTracker : Application.ActivityLifecycleCallbacks {
        private val alive = mutableSetOf<Activity>()
        private var resumed: Activity? = null
        private var lastCreatedWithState: Boolean? = null

        val noneAlive: Boolean get() = synchronized(this) { alive.isEmpty() }
        val isMainActivityResumed: Boolean get() = synchronized(this) { resumed is MainActivity }
        val lastCreatedFromSavedState: Boolean get() = synchronized(this) { lastCreatedWithState == true }

        fun forgetLastCreated() = synchronized(this) { lastCreatedWithState = null }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = synchronized(this) {
            alive += activity
            lastCreatedWithState = savedInstanceState != null
        }

        override fun onActivityResumed(activity: Activity) = synchronized(this) { resumed = activity }

        override fun onActivityPaused(activity: Activity) = synchronized(this) {
            if (resumed === activity) resumed = null
        }

        override fun onActivityDestroyed(activity: Activity) = synchronized(this) {
            alive -= activity
            if (resumed === activity) resumed = null
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    private companion object {
        const val TAG = "UiTestScreen"
        const val NOT_RESPONDING_WATCHER = "notResponding"
        const val PNG_QUALITY = 100 // Ignored for PNG, which is lossless.
        const val NETWORK_TIMEOUT_MS = 30_000L
        const val ANIMATOR_SCALE = "animator_duration_scale"
        const val INTERACTIVE_TIMEOUT = "accessibility_interactive_ui_timeout_ms"
        const val TIME_12_24 = "time_12_24"
        const val MS_PER_SECOND = 1_000
        const val SET_ALWAYS_FINISH = "android.permission.SET_ALWAYS_FINISH"
    }
}
