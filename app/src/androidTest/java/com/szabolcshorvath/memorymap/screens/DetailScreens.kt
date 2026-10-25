package com.szabolcshorvath.memorymap.screens

/** A memory opened from the map or the timeline (one page of the memory pager). */
object MemoryScreen {
    private const val EDIT = "Edit Memory"

    fun assertShown() = Ui.assertShown("A memory") { hasDescription(EDIT) }

    fun assertGone() = Ui.assertGone("The memory") { hasDescription(EDIT) }

    fun assertShowing(title: String) = Ui.assertShown("The memory \"$title\"") { hasId("tv_title") && hasText(title) }

    fun showOnMap() = tap { hasText("Show on Map") }

    fun showOnTimeline() = tap { hasText("Show on Timeline") }

    fun edit() = tap { hasDescription(EDIT) }

    fun openFirstPhoto() = tap { hasDescription("Open photo") }

    private fun tap(condition: android.view.accessibility.AccessibilityNodeInfo.() -> Boolean) {
        Ui.find(condition = condition).click()
        Ui.device.waitForIdle()
    }
}

/** The full-screen photo and video viewer. */
object MediaViewerScreen {
    fun assertShowingPhoto() = Ui.assertShown("The full-screen photo") { hasDescription("Photo") }

    fun assertGone() = Ui.assertGone("The full-screen viewer") { hasDescription("Photo") }
}

/** The location picker opened from the Add form. */
object LocationPickerScreen {
    private const val CONFIRM = "Confirm Location"

    fun assertShown() = Ui.assertShown("The location picker") { hasText(CONFIRM) }

    fun assertGone() = Ui.assertGone("The location picker") { hasText(CONFIRM) }

    /** Taps the middle of the map, which picks that spot. */
    fun tapMap() {
        val map = Ui.find { hasDescription("Google Map") }
        map.click()
        Ui.device.waitForIdle()
    }

    /** Waits until a spot is picked: its marker shows on the map. */
    fun assertLocationPicked() = Ui.assertShown("A picked location") {
        contentDescription?.toString()?.startsWith("Selected Location") == true
    }

    fun confirm() {
        Ui.find { hasText(CONFIRM) }.click()
        Ui.device.waitForIdle()
    }
}
