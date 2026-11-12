package com.szabolcshorvath.memorymap.ui.shell

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** A message the shell shows in its snackbar, over the tab bar. */
sealed interface ShellMessage {
    /** At start: photos need re-linking and the app has no media access; the action asks for it. */
    data object RelinkNeedsAccess : ShellMessage
}

/**
 * Where the app is: the selected tab, the tabs composed so far, and the screens over the tabs. The tab and the screens
 * survive process death (in [SavedStateHandle]); the composed tabs start again from the selected one after the activity
 * is recreated ([onActivityRecreated]), so only the shown tab is built.
 */
class ShellViewModel(savedState: SavedStateHandle) : ViewModel() {
    private val restored = savedState.get<Bundle>(STATE)

    var tab by mutableStateOf(restored?.getString(TAB)?.let(Tab::valueOf) ?: Tab.MAP)
        private set

    /** The tabs composed so far, the selected one included. */
    val visited: SnapshotStateList<Tab> = mutableStateListOf(tab)

    /** The screens over the tabs, starting with [Detail.None]. */
    val details: SnapshotStateList<Detail> = mutableStateListOf<Detail>().apply {
        val saved = restored?.let { BundleCompat.getParcelableArrayList(it, DETAILS, Bundle::class.java) }
        addAll(saved?.mapNotNull { it.toDetail() }?.takeIf { it.firstOrNull() == Detail.None } ?: listOf(Detail.None))
    }

    /** Whether a screen is shown over the tabs. */
    val hasDetail get() = details.size > 1

    /** The media viewer shows (started): the tab bar and the system bars hide. */
    var mediaViewerShown by mutableStateOf(false)

    /** The location picker shows (started): the tab bar hides, so the map below it has the whole window. */
    var locationPickerShown by mutableStateOf(false)

    private val _messages = Channel<ShellMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        savedState.setSavedStateProvider(STATE) {
            Bundle().apply {
                putString(TAB, tab.name)
                putParcelableArrayList(DETAILS, ArrayList(details.map { it.toBundle() }))
            }
        }
    }

    /** The activity was recreated (rotation, process death): only the shown tab is composed again. */
    fun onActivityRecreated() {
        visited.retainAll { it == tab }
        if (visited.isEmpty()) visited += tab
    }

    /** Shows [tab], closing the screens over the tabs. */
    fun select(tab: Tab) {
        this.tab = tab
        if (tab !in visited) visited += tab
        closeDetails()
    }

    /** Shows [detail] over the current screen. */
    fun open(detail: Detail) {
        details += detail
    }

    /** Closes the top screen over the tabs, if there is one. */
    fun back() {
        if (hasDetail) details.removeAt(details.lastIndex)
    }

    /** Closes [detail] if it is the top screen; a second close of the same screen does nothing. */
    fun close(detail: Detail) {
        if (hasDetail && details.last() == detail) details.removeAt(details.lastIndex)
    }

    private fun closeDetails() {
        while (hasDetail) details.removeAt(details.lastIndex)
    }

    fun show(message: ShellMessage) {
        _messages.trySend(message)
    }

    private companion object {
        const val STATE = "shell"
        const val TAB = "tab"
        const val DETAILS = "details"
    }
}
