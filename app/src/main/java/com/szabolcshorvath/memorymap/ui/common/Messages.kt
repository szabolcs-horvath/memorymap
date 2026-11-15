package com.szabolcshorvath.memorymap.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shows the user a message without an action in the shell's snackbar, over the tab bar, whatever screen is shown. The
 * shell provides it to every screen ([LocalMessages]). A modal bottom sheet is a window of its own, above the shell's
 * snackbar: a message that leaves the sheet open shows in the sheet instead.
 */
fun interface Messages {
    /** Shows [text] for a short time, or for a long one if [long]. */
    fun show(@StringRes text: Int, long: Boolean)
}

/** The shell's [Messages]. */
val LocalMessages = staticCompositionLocalOf<Messages> { error("Messages are shown by the shell, which isn't around this") }
