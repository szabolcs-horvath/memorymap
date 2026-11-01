package com.szabolcshorvath.memorymap.screens

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import java.util.regex.Pattern

/** The system's share sheet, which another app shows over this one. */
object ShareSheet {
    // Android 14 and newer show it from its own app; older versions from "android".
    private val PACKAGES = Pattern.compile("com\\.android\\.intentresolver|android")

    fun assertShown() = assertTrue(
        "The share sheet did not open",
        Ui.device.wait(Until.hasObject(By.pkg(PACKAGES)), TIMEOUT_MS) == true
    )

    private const val TIMEOUT_MS = 15_000L
}
