package com.szabolcshorvath.memorymap.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * A question with a confirm and a cancel button, as Material 3's dialog. Keep whether it is open, and what it acts on,
 * in `rememberSaveable` or the ViewModel's saved state, so it comes back after the phone is turned and after process
 * death. A tap outside it, or Back, is a cancel. (Its four texts and two answers are its parameters.)
 */
@Suppress("LongParameterList")
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    cancel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(cancel) } },
    )
}
