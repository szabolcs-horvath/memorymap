package com.szabolcshorvath.memorymap.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.platform.app.InstrumentationRegistry
import com.szabolcshorvath.memorymap.screens.ComposeUi.press
import com.szabolcshorvath.memorymap.screens.ComposeUi.tap

/** The Settings tab. Found through [ComposeUi]. */
object SettingsScreen : TabScreen {
    private const val SHOW_FRAGMENTS = "Show memory fragment markers"
    private const val CONNECT = "Connect"

    // By the scrolling page, which is on screen wherever it was scrolled to: the tab keeps its scroll position.
    override fun assertShown() {
        ComposeUi.waitFor("Settings", hasTestTag(PAGE))
    }

    override fun assertGone() = ComposeUi.assertGone("Settings", hasTestTag(PAGE))

    fun assertSignedOut() {
        ComposeUi.scrollTo("The Connect to Google Drive button", hasText(CONNECT))
    }

    fun assertSignedIn(email: String) {
        ComposeUi.scrollTo("The signed-in account $email", hasTestTag("tv_accountName") and hasText(email), unmerged = true)
    }

    private fun backup(label: String) = hasTestTag("tv_backup_name") and hasText(label)

    /** The backup list shows a backup with [label] ("Manual Backup", "Automatic Backup" or the file name). */
    fun assertBackupShown(label: String) {
        ComposeUi.scrollTo("The backup \"$label\"", backup(label), unmerged = true)
    }

    fun assertBackupGone(label: String) = ComposeUi.assertGone("The backup \"$label\"", backup(label), unmerged = true)

    /** Waits up to [timeoutMs] for a backup with [label] to appear in the list, such as an automatic backup. */
    fun assertBackupAppears(label: String, timeoutMs: Long) {
        ComposeUi.waitFor("The backup \"$label\"", backup(label), unmerged = true, timeoutMs = timeoutMs)
        assertBackupShown(label)
    }

    /** Under the newest backups, a row opens the page with all [total] of them. */
    fun assertShowAllBackups(total: Int) {
        ComposeUi.scrollTo("Show all backups, with $total backups", hasText(SHOW_ALL) and hasText("$total backups"))
    }

    fun assertNoShowAllBackups() = ComposeUi.assertGone("Show all backups", hasText(SHOW_ALL))

    /** Opens the page with every backup; [assertBackupShown] and the restore and delete buttons work on it too. */
    fun showAllBackups() {
        ComposeUi.scrollTo("Show all backups", hasText(SHOW_ALL)).press()
        assertAllBackupsShown()
    }

    fun assertAllBackupsShown() {
        ComposeUi.waitFor("All backups", hasTestTag(ALL_BACKUPS_PAGE))
    }

    /** Leaves the page with every backup by its back arrow. */
    fun closeAllBackups() {
        ComposeUi.waitFor("The back arrow", hasContentDescription("Back")).performClick()
        assertAllBackupsGone()
    }

    fun assertAllBackupsGone() = ComposeUi.assertGone("All backups", hasTestTag(ALL_BACKUPS_PAGE))

    /** Pulls the page down from its top, which loads the backup list again. */
    fun refreshBackups() {
        val page = ComposeUi.waitFor("Settings", hasTestTag(PAGE))
        page.performScrollToIndex(0)
        page.performTouchInput { swipeDown() }
    }

    /** The line above the backup list says it couldn't be loaded, with the message [message] (a string resource). */
    fun assertBackupListProblem(message: Int) {
        val text = InstrumentationRegistry.getInstrumentation().targetContext.getString(message)
        ComposeUi.scrollTo("The backup list problem \"$text\"", hasText(text))
    }

    fun assertNoBackupListProblem() =
        ComposeUi.assertGone("A backup list problem", hasTestTag("tv_backupListProblem"), unmerged = true)

    fun assertAllowAccessGone() = ComposeUi.assertGone("The Allow access button", hasText(ALLOW_ACCESS))

    fun allowDriveAccess() {
        ComposeUi.scrollTo("The Allow access button", hasText(ALLOW_ACCESS)).press()
    }

    private const val ALLOW_ACCESS = "Allow access"

    /** The first backup in the list is the newest. */
    fun tapRestoreOnFirstBackup() = tapOnFirstBackup("Restore")

    fun tapDeleteOnFirstBackup() = tapOnFirstBackup("Delete")

    private fun tapOnFirstBackup(button: String) {
        ComposeUi.scrollTo("A backup's $button button", hasContentDescription(button) and isEnabled()).press()
    }

    fun signIn() = tapButton(CONNECT)

    fun signOut() = tapButton("Disconnect")

    fun backUpNow() = tapButton("Back up now")

    /**
     * Taps "Re-link photos and videos" and waits a moment for the run to start (the item turns off while it runs), so
     * [waitForRelinkToFinish] doesn't take the item, still on from before the tap, for a finished run. A quick run may
     * be over before that is seen.
     */
    fun relinkMedia() {
        tapButton(RELINK)
        Ui.waitUntil(RELINK_START_MS) { !ComposeUi.exists(relinkItem and isEnabled()) }
    }

    fun sendLogs() = tapButton("Send debug logs")

    /** Waits until the re-link run is over: the screen's buttons work again. */
    fun waitForRelinkToFinish() {
        ComposeUi.scrollTo("The Re-link item", relinkItem)
        ComposeUi.waitFor("The Re-link item, enabled again", relinkItem and isEnabled(), timeoutMs = Ui.SLOW_TIMEOUT_MS)
    }

    private val relinkItem = hasTestTag("bt_relinkMedia")

    /**
     * Waits until a backup, restore or delete and anything after it (a restore re-links media) is over: the
     * loading overlay is gone and the buttons work again.
     */
    fun waitUntilIdle() {
        if (!Ui.waitUntil(BUSY_TIMEOUT_MS) { !ComposeUi.exists(hasTestTag("loadingOverlay")) }) {
            throw AssertionError("Settings is still busy")
        }
        waitForRelinkToFinish()
    }

    /** The result line of the last backup, restore or delete starts with [text], such as "Backup successful". */
    fun assertResult(text: String) {
        val result = hasTestTag("tv_backupStatusDetails") and ComposeUi.textStartsWith(text)
        ComposeUi.scrollTo("The result \"$text\"", result, unmerged = true)
    }

    fun assertAutomaticBackupPending() {
        val pending = "Your latest changes will be backed up shortly (when online)."
        ComposeUi.scrollTo("The automatic backup line", hasText(pending))
    }

    /** Opens the colour presets section and adds the colour shown as a new preset. */
    fun addColorPreset() {
        ColorPresetsSection.open()
        ColorPresetsSection.addCurrentColor()
    }

    fun setShowFragmentMarkers(on: Boolean) = setSwitch(SHOW_FRAGMENTS, on)

    fun setMarkerClustering(on: Boolean) = setSwitch(CLUSTERING, on)

    fun assertShowFragmentMarkers(on: Boolean) = assertSwitch(SHOW_FRAGMENTS, on)

    fun assertMarkerClustering(on: Boolean) = assertSwitch(CLUSTERING, on)

    /** Picks [label] ("Last 7 days", "All time", ...) as the date filter the map opens with. */
    fun setDefaultMapFilter(label: String) {
        // The setting's value is a line inside its row: a tap on it opens the menu.
        ComposeUi.scrollTo("The default map filter", defaultFilter, unmerged = true).tap()
        ComposeUi.waitFor("\"$label\" in the menu", hasText(label) and isMenuChoice).performClick()
    }

    fun assertDefaultMapFilter(label: String) {
        ComposeUi.scrollTo("The default map filter \"$label\"", defaultFilter and hasText(label), unmerged = true)
    }

    private val defaultFilter = hasTestTag("spinner_defaultFilter")
    private val isMenuChoice = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    // A switch stays disabled until the saved setting is loaded.
    private fun switch(label: String) = hasText(label) and isToggleable()

    private fun setSwitch(label: String, on: Boolean) {
        val switch = ComposeUi.scrollTo("The switch \"$label\"", switch(label) and isEnabled())
        if (!ComposeUi.exists(switch(label) and switchedOn(on))) switch.press()
        assertSwitch(label, on)
    }

    private fun assertSwitch(label: String, on: Boolean) {
        ComposeUi.scrollTo("The switch \"$label\"", switch(label))
        ComposeUi.waitFor("\"$label\" turned ${if (on) "on" else "off"}", switch(label) and isEnabled() and switchedOn(on))
    }

    private fun switchedOn(on: Boolean) = if (on) isOn() else isOff()

    private fun tapButton(text: String) {
        ComposeUi.scrollTo("\"$text\"", hasText(text) and isEnabled()).press()
    }

    private const val CLUSTERING = "Enable marker clustering"
    private const val RELINK = "Re-link photos and videos"
    private const val PAGE = "scroll_settings"
    private const val ALL_BACKUPS_PAGE = "scroll_all_backups"
    private const val SHOW_ALL = "Show all backups"
    private const val BUSY_TIMEOUT_MS = 30_000L
    private const val RELINK_START_MS = 2_000L
}
