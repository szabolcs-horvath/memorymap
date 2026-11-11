package com.szabolcshorvath.memorymap.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.fragment.BackupItem
import com.szabolcshorvath.memorymap.fragment.SettingsUiState
import com.szabolcshorvath.memorymap.util.DateFilterOption

// The UI tests find these elements by these names (screens/SettingsScreen), the names the View version's ids had.
private const val TAG_PAGE = "scroll_settings"
private const val TAG_DEFAULT_FILTER = "spinner_defaultFilter"
private const val TAG_BUSY = "loadingOverlay"
private const val TAG_RELINK = "bt_relinkMedia"
private const val TAG_SEND_LOGS = "bt_sendLogs"
private const val TAG_ALL_BACKUPS_PAGE = "scroll_all_backups"

/** The default filter's arrow points up while its menu is open. */
private const val ARROW_OPEN = 180f

/** Below this height a window is compact (Material's window size classes): the top app bar is a small one. */
private val COMPACT_HEIGHT = 480.dp

/** What the user can do on the Settings tab. What needs an activity, or asks first, is the fragment's to do. */
interface SettingsActions {
    fun setShowFragmentMarkers(show: Boolean)
    fun setMarkerClustering(enabled: Boolean)
    fun setDefaultFilter(option: DateFilterOption)
    fun signIn()
    fun signOut()
    fun backUpNow()
    fun refreshBackups()
    fun allowDriveAccess()

    /** Restores [backup], after asking. */
    fun restoreBackup(backup: BackupItem)

    /** Deletes [backup], after asking. */
    fun deleteBackup(backup: BackupItem)
    fun relinkMedia()
    fun sendLogs()
}

/**
 * The Settings tab: a list of settings in groups (the map, its marker colours, backups, the media library) under a large
 * top app bar that collapses as the list scrolls. It shows the newest backups; the rest are on a page of their own. Pulling
 * a list down loads the backups again. While something runs that the screen waits for ([SettingsUiState.busy]), its
 * buttons are off and a card at the bottom says what runs.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, presetActions: ColorPresetActions) {
    val enabled = state.busy == null
    // The page with every backup, opened from the main page's "Show all backups"; Back returns to where it was.
    var allBackupsShown by rememberSaveable { mutableStateOf(false) }
    val mainList = rememberLazyListState()
    BackHandler(enabled = allBackupsShown) { allBackupsShown = false }
    Box(
        Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
    ) {
        AnimatedContent(allBackupsShown, label = "settings_page") { allBackups ->
            if (allBackups) {
                SettingsPage(
                    title = R.string.all_backups_title,
                    onBack = { allBackupsShown = false },
                    state = state,
                    actions = actions,
                    listState = rememberLazyListState(),
                    listTag = TAG_ALL_BACKUPS_PAGE,
                ) {
                    item(key = "result") { ResultBanner(state.result) }
                    backupList(state, state.backups, actions, enabled)
                }
            } else {
                SettingsPage(R.string.settings_title, onBack = null, state, actions, mainList, TAG_PAGE) {
                    mapSection(state, actions)
                    colorPresetsSection(state.colorPresets, presetActions, enabled)
                    backupSection(state, actions, enabled) { allBackupsShown = true }
                    mediaSection(actions, enabled)
                }
            }
        }
        BusyCard(state, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * A page of the Settings tab: a list under a large top app bar that collapses as the list scrolls (a small one, with
 * [onBack]'s arrow, when it is set). Pulling the list down loads the backups again.
 */
@Composable
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
private fun SettingsPage(
    @StringRes title: Int,
    onBack: (() -> Unit)?,
    state: SettingsUiState,
    actions: SettingsActions,
    listState: LazyListState,
    listTag: String,
    content: LazyListScope.() -> Unit,
) {
    // On a short window (a phone turned sideways) the large title would take a third of the screen: a small bar, which
    // stays put. Each keeps its own scroll state, so the small one never takes on how far the large one had collapsed.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compact = windowHeight < COMPACT_HEIGHT
    val scrollBehavior = if (compact) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        val titleText = @Composable { Text(stringResource(title)) }
        val navigationIcon = @Composable {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                }
            }
        }
        if (compact) {
            TopAppBar(titleText, navigationIcon = navigationIcon, windowInsets = WindowInsets(0), scrollBehavior = scrollBehavior)
        } else {
            LargeFlexibleTopAppBar(
                titleText,
                navigationIcon = navigationIcon,
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
            )
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions::refreshBackups,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .testTag(listTag),
                state = listState,
                contentPadding = PaddingValues(bottom = PAGE_BOTTOM_PADDING),
                content = content,
            )
        }
    }
}

/** The page's side margin, and the space inside a list item before its text (Material 3's list item padding). */
internal val LIST_MARGIN = 16.dp
internal val ITEM_PADDING = 16.dp

/** The gap between the items of a group: Material 3 Expressive's segmented list, as wide as the Timeline's. */
internal val SEGMENT_GAP = 4.dp

/** The gap between standalone items, such as the map's settings: wider than a list's, so they read as separate. */
private val STANDALONE_GAP = 8.dp

/** Room below the last item, so the busy card doesn't cover it. */
private val PAGE_BOTTOM_PADDING = 96.dp

/** The modifier of item [index] in a group: the page's margin, and the gap above every item but the first. */
internal fun Modifier.segment(index: Int): Modifier =
    padding(horizontal = LIST_MARGIN).padding(top = if (index == 0) 0.dp else SEGMENT_GAP)

/** Filled items on the page's surface, as on the Timeline. */
@Composable
internal fun segmentColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

/**
 * A switch row's colours: as any other item's while it is on. (A checked list item is drawn as selected, but a setting
 * turned on is not a selection.)
 */
@Composable
private fun switchColors(): ListItemColors {
    val colors = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = colors.surfaceContainer,
        selectedContainerColor = colors.surfaceContainer,
        selectedContentColor = colors.onSurface,
        selectedTrailingContentColor = colors.onSurfaceVariant,
    )
}

/** The label above a group, lined up with its items' text. */
internal fun LazyListScope.sectionHeader(@StringRes text: Int) = item(key = "header_$text", contentType = "header") {
    Text(
        text = stringResource(text),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = LIST_MARGIN + ITEM_PADDING, end = LIST_MARGIN, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * A setting of its own: a card with all its corners round, [STANDALONE_GAP] below the one above it unless it is the
 * [first] of its section. Settings that only share a section aren't a list (their rows differ, and none comes or goes),
 * so they aren't drawn as one; lists such as the backups are.
 */
private fun Modifier.standalone(first: Boolean): Modifier =
    padding(horizontal = LIST_MARGIN).padding(top = if (first) 0.dp else STANDALONE_GAP)

/** A standalone item's shapes: a setting turned on keeps its shape (a checked list item otherwise takes another). */
@Composable
private fun standaloneShapes(): ListItemShapes {
    val shapes = ListItemDefaults.segmentedShapes(0, 1)
    return shapes.copy(selectedShape = shapes.shape)
}

private fun LazyListScope.mapSection(state: SettingsUiState, actions: SettingsActions) {
    sectionHeader(R.string.settings_map_section)
    item(key = "fragments") {
        SwitchItem(first = true, R.string.show_fragment_markers, state.map.showFragmentMarkers, actions::setShowFragmentMarkers)
    }
    item(key = "clustering") {
        SwitchItem(first = false, R.string.enable_marker_clustering, state.map.markerClustering, actions::setMarkerClustering)
    }
    item(key = "filter") { DefaultFilterItem(state.map.defaultFilter, actions::setDefaultFilter) }
}

/** A setting turned on or off; [checked] is null until the stored value is loaded, and the switch is off till then. */
@Composable
private fun SwitchItem(first: Boolean, @StringRes label: Int, checked: Boolean?, onChange: (Boolean) -> Unit) {
    SegmentedListItem(
        checked = checked == true,
        onCheckedChange = onChange,
        shapes = standaloneShapes(),
        modifier = Modifier.standalone(first),
        enabled = checked != null,
        trailingContent = { Switch(checked = checked == true, onCheckedChange = null, enabled = checked != null) },
        colors = switchColors(),
    ) {
        Text(stringResource(label))
    }
}

/**
 * The date filter the map opens with: shown under the setting's name, chosen from a menu that opens from the arrow at
 * the row's end. The arrow turns up while the menu is open.
 */
@Composable
private fun DefaultFilterItem(filter: DateFilterOption?, onChange: (DateFilterOption) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (menuOpen) ARROW_OPEN else 0f, label = "filter_arrow")
    SegmentedListItem(
        onClick = { menuOpen = true },
        shapes = standaloneShapes(),
        modifier = Modifier.standalone(first = false),
        enabled = filter != null,
        supportingContent = {
            Text(filter?.let { stringResource(it.label) }.orEmpty(), Modifier.testTag(TAG_DEFAULT_FILTER))
        },
        trailingContent = {
            Box {
                Icon(
                    painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    modifier = Modifier.rotate(arrowRotation),
                )
                DateFilterMenu(
                    expanded = menuOpen,
                    selected = filter,
                    onDismiss = { menuOpen = false },
                    onPick = { option ->
                        menuOpen = false
                        onChange(option)
                    },
                )
            }
        },
        colors = segmentColors(),
    ) {
        Text(stringResource(R.string.default_map_filter))
    }
}

/** Material 3 Expressive's menu of choices: the chosen one is filled and has a check mark. */
@Composable
internal fun DateFilterMenu(
    expanded: Boolean,
    selected: DateFilterOption?,
    onDismiss: () -> Unit,
    onPick: (DateFilterOption) -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        val options = DateFilterOption.entries
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
            options.forEachIndexed { i, option ->
                SelectableDropdownMenuItem(
                    selected = option == selected,
                    onClick = { onPick(option) },
                    text = { Text(stringResource(option.label)) },
                    shapes = MenuDefaults.itemShape(i, options.size),
                    selectedLeadingIcon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
                )
            }
        }
    }
}

private fun LazyListScope.mediaSection(actions: SettingsActions, enabled: Boolean) {
    sectionHeader(R.string.media_maintenance_title)
    item(key = "relink") {
        ActionItem(
            first = true,
            icon = R.drawable.ic_photo_library,
            label = R.string.relink_media,
            description = R.string.media_maintenance_description,
            tag = TAG_RELINK,
            enabled = enabled,
            onClick = actions::relinkMedia,
        )
    }
    item(key = "send_logs") {
        ActionItem(
            first = false,
            icon = R.drawable.ic_share,
            label = R.string.send_logs,
            description = R.string.send_logs_description,
            tag = TAG_SEND_LOGS,
            enabled = enabled,
            onClick = actions::sendLogs,
        )
    }
}

/**
 * A standalone item that does something when tapped, with a line saying what. Material's list items centre the icon on
 * a two-line item and put it at the top of a longer one.
 */
@Composable
@Suppress("LongParameterList")
private fun ActionItem(
    first: Boolean,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    @StringRes description: Int,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = standaloneShapes(),
        modifier = Modifier
            .standalone(first)
            .testTag(tag),
        enabled = enabled,
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        supportingContent = { Text(stringResource(description)) },
        colors = segmentColors(),
    ) {
        Text(stringResource(label))
    }
}

/** What runs while the screen waits: a wavy progress bar and what it is doing, in a card at the bottom. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun BusyCard(state: SettingsUiState, modifier: Modifier = Modifier) {
    // Pulling the list down shows its own indicator.
    val busy = state.busy?.takeIf { !state.refreshing }
    AnimatedVisibility(visible = busy != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        val shown = remember { mutableStateOf(busy) }
        if (busy != null) shown.value = busy
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag(TAG_BUSY),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                shown.value?.status?.let { status ->
                    Text(
                        status,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
