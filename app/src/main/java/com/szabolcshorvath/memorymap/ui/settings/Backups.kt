package com.szabolcshorvath.memorymap.ui.settings

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.backup.AutomaticBackupStatus
import com.szabolcshorvath.memorymap.backup.DriveProblem
import com.szabolcshorvath.memorymap.fragment.BackupItem
import com.szabolcshorvath.memorymap.fragment.OperationResult
import com.szabolcshorvath.memorymap.fragment.SettingsUiState
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import java.time.Instant
import java.time.ZoneId

// The UI tests find these elements by these names (screens/SettingsScreen), the names the View version's ids had.
private const val TAG_ACCOUNT = "tv_accountName"
private const val TAG_RESULT = "tv_backupStatusDetails"
private const val TAG_LIST_PROBLEM = "tv_backupListProblem"
private const val TAG_BACKUP_NAME = "tv_backup_name"

private const val AUTOMATIC_BACKUP_PREFIX = "MemoryMap_Automatic_Backup_"
private const val MANUAL_BACKUP_PREFIX = "MemoryMap_Manual_Backup_"

/** How many backups the main page shows, newest first. */
private const val RECENT_BACKUPS = 5

/** The backups group of the main page: the Google Drive account, "Back up now", and the newest backups. */
internal fun LazyListScope.backupSection(
    state: SettingsUiState,
    actions: SettingsActions,
    enabled: Boolean,
    onShowAll: () -> Unit,
) {
    sectionHeader(R.string.backup_section_title)
    item(key = "account") { AccountItem(state.email, actions, enabled) }
    if (state.email == null) return

    item(key = "backup_now") {
        Button(
            onClick = actions::backUpNow,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LIST_MARGIN)
                .padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.backup_now))
        }
    }
    item(key = "automatic_backup") { AutomaticBackupLine(state.automaticBackup) }
    item(key = "result") { ResultBanner(state.result) }
    sectionHeader(R.string.available_backups)
    // The newest few, so the page stays short; the rest are a tap away.
    val hasMore = state.backups.size > RECENT_BACKUPS
    backupList(state, state.backups.take(RECENT_BACKUPS), actions, enabled, extraRows = if (hasMore) 1 else 0)
    if (hasMore) {
        item(key = "all_backups") { ShowAllBackupsItem(RECENT_BACKUPS, state.backups.size, onShowAll) }
    }
}

/**
 * [backups] as a group of rows, under why the list couldn't be loaded if it couldn't; [extraRows] more items follow in
 * the same group.
 */
internal fun LazyListScope.backupList(
    state: SettingsUiState,
    backups: List<BackupItem>,
    actions: SettingsActions,
    enabled: Boolean,
    extraRows: Int = 0,
) {
    state.backupListProblem?.let { problem ->
        item(key = "list_problem") { BackupListProblem(problem, actions::allowDriveAccess, enabled) }
    }
    val count = backups.size + extraRows
    itemsIndexed(backups, key = { _, backup -> "backup_${backup.id}" }, contentType = { _, _ -> "backup" }) { i, backup ->
        BackupRow(backup, i, count, actions, enabled)
    }
}

/** The last row of the main page's backups: opens the page with all [total] of them. */
@Composable
private fun ShowAllBackupsItem(index: Int, total: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, index + 1),
        modifier = Modifier.segment(index),
        supportingContent = { Text(pluralStringResource(R.plurals.backup_count, total, total)) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        colors = segmentColors(),
    ) {
        Text(stringResource(R.string.show_all_backups))
    }
}

/** The Google Drive account: connect it, or see which one is connected and disconnect it. */
@Composable
private fun AccountItem(email: String?, actions: SettingsActions, enabled: Boolean) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        modifier = Modifier.segment(0),
        leadingContent = { Icon(painterResource(R.drawable.ic_cloud), contentDescription = null) },
        supportingContent = {
            if (email == null) {
                Text(stringResource(R.string.backup_description))
            } else {
                Text(email, Modifier.testTag(TAG_ACCOUNT))
            }
        },
        trailingContent = {
            // Disconnecting is rare and undoes the setup: a quiet text button, as Material says destructive actions are.
            if (email == null) {
                FilledTonalButton(onClick = actions::signIn, enabled = enabled) { Text(stringResource(R.string.connect_drive)) }
            } else {
                TextButton(onClick = actions::signOut, enabled = enabled) { Text(stringResource(R.string.disconnect)) }
            }
        },
        colors = segmentColors(),
    ) {
        Text(stringResource(R.string.google_drive))
    }
}

/** A small line under "Back up now": automatic backups never block the screen. */
@Composable
private fun AutomaticBackupLine(status: AutomaticBackupStatus) {
    val text = when (status) {
        AutomaticBackupStatus.IDLE -> return
        AutomaticBackupStatus.PENDING -> R.string.auto_backup_pending
        AutomaticBackupStatus.RUNNING -> R.string.auto_backup_running
        AutomaticBackupStatus.FAILED -> R.string.auto_backup_failed
    }
    Row(
        Modifier.padding(start = LIST_MARGIN + ITEM_PADDING, end = LIST_MARGIN, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == AutomaticBackupStatus.RUNNING) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How the last backup, restore or delete went: a tonal banner, with an icon that says which. */
@Composable
internal fun ResultBanner(result: OperationResult?) {
    AnimatedVisibility(visible = result != null, enter = fadeIn(), exit = fadeOut()) {
        // Keeps showing the last result while it fades out.
        val shown = remember { mutableStateOf(result) }
        if (result != null) shown.value = result
        val current = shown.value ?: return@AnimatedVisibility
        val colors = MaterialTheme.colorScheme
        val container = if (current.isError) colors.errorContainer else colors.secondaryContainer
        val content = if (current.isError) colors.onErrorContainer else colors.onSecondaryContainer
        val iconColor = if (current.isError) colors.error else successColor()
        Surface(
            color = container,
            contentColor = content,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LIST_MARGIN)
                .padding(top = 16.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (current.isError) R.drawable.ic_error else R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = iconColor,
                )
                Spacer(Modifier.width(12.dp))
                Text(current.message, Modifier.testTag(TAG_RESULT), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The app's success colour, from the XML theme (Material 3 has no colour role for it). */
@Composable
private fun successColor(): Color {
    val context = LocalContext.current
    return Color(MaterialColors.getColor(context, R.attr.colorSuccess, "ResultBanner"))
}

/** Why the backup list couldn't be loaded; when Drive access lapsed, a button to allow it again. */
@Composable
private fun BackupListProblem(problem: DriveProblem, onAllowAccess: () -> Unit, enabled: Boolean) {
    Column(Modifier.padding(start = LIST_MARGIN + ITEM_PADDING, end = LIST_MARGIN, bottom = 8.dp)) {
        Text(
            text = stringResource(
                when (problem) {
                    DriveProblem.NEEDS_ACCESS -> R.string.backup_list_needs_access
                    DriveProblem.NO_CONNECTION -> R.string.backup_list_no_connection
                    DriveProblem.DRIVE_UNAVAILABLE -> R.string.backup_list_drive_unavailable
                    DriveProblem.OTHER -> R.string.backup_list_failed
                }
            ),
            modifier = Modifier.testTag(TAG_LIST_PROBLEM),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (problem == DriveProblem.NEEDS_ACCESS) {
            FilledTonalButton(onClick = onAllowAccess, enabled = enabled, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.allow_drive_access))
            }
        }
    }
}

/** A backup: which kind, when and how big, with buttons to restore and delete it. */
@Composable
private fun BackupRow(backup: BackupItem, index: Int, count: Int, actions: SettingsActions, enabled: Boolean) {
    val context = LocalContext.current
    val label = when {
        backup.name.startsWith(AUTOMATIC_BACKUP_PREFIX) -> stringResource(R.string.backup_automatic)
        backup.name.startsWith(MANUAL_BACKUP_PREFIX) -> stringResource(R.string.backup_manual)
        else -> backup.name
    }
    val date = backup.modifiedMillis?.let { millis ->
        DateTimeFormatterUtil.dateTime(context, Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()), withSeconds = true)
    } ?: stringResource(R.string.unknown_date)
    val size = Formatter.formatFileSize(context, backup.size)
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = Modifier.segment(index),
        supportingContent = { Text(stringResource(R.string.backup_details, date, size)) },
        trailingContent = {
            Row {
                IconButton(
                    onClick = { actions.restoreBackup(backup) },
                    enabled = enabled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(painterResource(R.drawable.ic_settings_backup_restore), stringResource(R.string.restore))
                }
                IconButton(
                    onClick = { actions.deleteBackup(backup) },
                    enabled = enabled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete))
                }
            }
        },
        colors = segmentColors(),
    ) {
        Text(label, Modifier.testTag(TAG_BACKUP_NAME))
    }
}
