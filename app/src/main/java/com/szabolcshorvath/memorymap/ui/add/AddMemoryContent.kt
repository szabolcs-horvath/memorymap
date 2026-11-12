package com.szabolcshorvath.memorymap.ui.add

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.ui.color.Hsv
import com.szabolcshorvath.memorymap.util.Coordinates
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

// The UI tests find these by name (screens/AddScreen).
internal const val TAG_PAGE = "scroll_add"
private const val TAG_TITLE = "titleInput"
private const val TAG_DESCRIPTION = "descriptionInput"
private const val TAG_LOCATION_ITEM = "bt_selectLocation"
internal const val TAG_LOCATION = "tv_location"
private const val TAG_SAVE = "bt_save"
private const val TAG_CLEAR = "bt_clear"

/** The page's side margin. */
internal val PAGE_MARGIN = 16.dp

/** The gap between the page's items. */
private val ITEM_GAP = 8.dp

/** On a window shorter than this (a phone turned sideways), the top app bar is a small one. */
private val COMPACT_HEIGHT = 480.dp

// The fixed items' places in the list, to scroll to the first field a save lacks.
private const val TITLE_INDEX = 0
private const val LOCATION_INDEX = 2
private const val FIRST_FRAGMENT_INDEX = 7

/** What the user can do on the Add form. What needs an activity, or asks first, is the route's to do. */
interface AddMemoryActions {
    fun changeTitle(text: String)
    fun changeDescription(text: String)

    /** Opens the location picker for the memory, or for one of its fragments. */
    fun pickLocation(part: FormPart)
    fun setAllDay(part: FormPart, allDay: Boolean)
    fun setOwnTime(localId: String, on: Boolean)
    fun setDate(part: FormPart, isStart: Boolean, date: LocalDate)
    fun setTime(part: FormPart, isStart: Boolean, time: LocalTime)
    fun setDateRange(part: FormPart, first: LocalDate, last: LocalDate)
    fun setColor(part: FormPart, color: Hsv)
    fun toggleColor(part: FormPart)
    fun addFragment()
    fun removeFragment(localId: String)
    fun pickMedia()
    fun removeMedia(item: SelectedMedia)

    /** Clears the form (discards the edit), after asking. */
    fun clear()
    fun save()
    fun onMessageShown()
}

/**
 * The Add tab: the form for a new memory or for editing one, under a large top app bar with its title and Clear, and
 * above a Save button that stays in reach. A date or time button opens Material 3's picker over it.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun AddMemoryContent(state: AddFormUiState, presets: List<HSVPreset>, actions: AddMemoryActions) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compact = windowHeight < COMPACT_HEIGHT
    val scrollBehavior = if (compact) TopAppBarDefaults.pinnedScrollBehavior() else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbars = remember { SnackbarHostState() }
    var picker by rememberSaveable(stateSaver = PickerRequest.Saver) { mutableStateOf<PickerRequest?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resources = LocalResources.current

    // A save that lacked something: once the tab is shown, scroll to the first field that needs it.
    LaunchedEffect(state.errorScrollRequest) {
        if (state.errorScrollRequest == 0) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        firstErrorIndex(state)?.let { listState.animateScrollToItem(it) }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        snackbars.showSnackbar(resources.getString(message))
        actions.onMessageShown()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            // The top bar keeps clear of the status bar; in landscape the form keeps clear of the sides and the bottom.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .semantics { testTagsAsResourceId = true },
        topBar = { FormTopBar(state, compact, scrollBehavior, actions::clear) },
        bottomBar = { SaveBar(state, actions::save) },
        snackbarHost = { SnackbarHost(snackbars) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(TAG_PAGE),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            formItems(state, presets, actions) { picker = it }
        }
    }

    picker?.let { request ->
        FormPicker(request, state, actions) { picker = null }
    }
}

private fun LazyListScope.textFields(state: AddFormUiState, actions: AddMemoryActions) {
    // Each field keeps the text as typed. A form cleared or filled for editing ([AddFormUiState.formVersion]) gets new
    // items, which start again from what the form holds.
    item(key = "title_${state.formVersion}") {
        var text by rememberSaveable { mutableStateOf(state.title) }
        val missing = state.showErrors && text.isBlank()
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                actions.changeTitle(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_MARGIN)
                .testTag(TAG_TITLE),
            label = { Text(stringResource(R.string.title_hint)) },
            isError = missing,
            supportingText = if (missing) {
                { Text(stringResource(R.string.save_needs_title)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
    item(key = "description_${state.formVersion}") {
        var text by rememberSaveable { mutableStateOf(state.description) }
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                actions.changeDescription(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_MARGIN)
                .testTag(TAG_DESCRIPTION),
            label = { Text(stringResource(R.string.description_hint)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** The index of the first item a save lacks: the title, the memory's place or a fragment's. */
private fun firstErrorIndex(state: AddFormUiState): Int? = when {
    state.title.isBlank() -> TITLE_INDEX
    state.place == null -> LOCATION_INDEX
    else -> state.fragments.indexOfFirst { it.lat == null || it.lng == null }.takeIf { it >= 0 }?.plus(FIRST_FRAGMENT_INDEX)
}

/**
 * Where the memory (or a fragment) happened, as a row that opens the location picker: the place's name and address, or
 * its coordinates. Without a place, and after a save that needed one, it says so in the error colour.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LocationItem(place: FormPlace?, showErrors: Boolean, @StringRes missingText: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val missing = showErrors && place == null
    val headline = when {
        place == null -> stringResource(R.string.no_location_selected)
        !place.name.isNullOrEmpty() -> place.name
        else -> Coordinates.format(context, place.lat, place.lng)
    }
    val supporting = when {
        missing -> stringResource(missingText)
        place != null && !place.name.isNullOrEmpty() -> place.address?.takeIf { it.isNotEmpty() }
        else -> null
    }
    val errorColor = MaterialTheme.colorScheme.error
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        modifier = Modifier
            .padding(horizontal = PAGE_MARGIN)
            .testTag(TAG_LOCATION_ITEM),
        leadingContent = {
            Icon(
                painterResource(R.drawable.ic_location_on),
                contentDescription = null,
                tint = if (missing) errorColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = supporting?.let { { Text(it, color = if (missing) errorColor else MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = { Icon(painterResource(R.drawable.ic_map), stringResource(R.string.select_on_map)) },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(headline, Modifier.testTag(TAG_LOCATION))
    }
}

/** A section's name above its items, as on the memory page. [trailing]: a short note at its end, such as a count. */
@Composable
internal fun SectionHeader(@StringRes text: Int, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = PAGE_MARGIN, end = PAGE_MARGIN, top = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(text),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Photos and videos: their count, the button that adds more, and the picked ones as a grid of tiles. */
private fun LazyListScope.mediaSection(state: AddFormUiState, actions: AddMemoryActions) {
    item(key = "media_header") {
        val count = state.media.size
        SectionHeader(R.string.photos_and_videos, pluralStringResource(R.plurals.items_selected, count, count))
    }
    item(key = "add_media") {
        FilledTonalButton(
            onClick = actions::pickMedia,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_MARGIN),
        ) {
            Icon(painterResource(R.drawable.ic_photo_library), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.add_photos))
        }
    }
    val rows = state.media.chunked(MEDIA_COLUMNS)
    items(rows.size, key = { "media_row_" + rows[it].first().uri }) { index ->
        Row(
            Modifier.padding(horizontal = PAGE_MARGIN),
            horizontalArrangement = Arrangement.spacedBy(MEDIA_GAP),
        ) {
            rows[index].forEach { item ->
                SelectedMediaTile(item, state.currentDeviceId, { actions.removeMedia(item) }, Modifier.weight(1f))
            }
            repeat(MEDIA_COLUMNS - rows[index].size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Save (or Save changes), pinned under the form; while saving, a wavy progress indicator takes its place. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun SaveBar(state: AddFormUiState, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = SAVE_BAR_HEIGHT)
                .padding(horizontal = PAGE_MARGIN, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isSaving) {
                CircularWavyProgressIndicator(Modifier.size(40.dp))
            } else {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_SAVE),
                ) {
                    Text(stringResource(if (state.editing) R.string.update_memory else R.string.save_memory))
                }
            }
        }
    }
}

private val SAVE_BAR_HEIGHT = 64.dp
internal const val MEDIA_COLUMNS = 3
internal val MEDIA_GAP = 4.dp

/** The page's title ("New memory" or "Edit memory") with Clear (Discard while editing): a small bar on a short window. */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
private fun FormTopBar(state: AddFormUiState, compact: Boolean, scrollBehavior: TopAppBarScrollBehavior, onClear: () -> Unit) {
    val title = @Composable { Text(stringResource(if (state.editing) R.string.edit_memory_title else R.string.new_memory_title)) }
    val clear = @Composable {
        TextButton(onClick = onClear, enabled = !state.isSaving, modifier = Modifier.testTag(TAG_CLEAR)) {
            Text(stringResource(if (state.editing) R.string.discard else R.string.clear))
        }
    }
    if (compact) {
        TopAppBar(title, actions = { clear() }, scrollBehavior = scrollBehavior)
    } else {
        LargeFlexibleTopAppBar(title, actions = { clear() }, scrollBehavior = scrollBehavior)
    }
}

/** The form's items, top to bottom; [onPick] opens a date or time picker. */
private fun LazyListScope.formItems(
    state: AddFormUiState,
    presets: List<HSVPreset>,
    actions: AddMemoryActions,
    onPick: (PickerRequest) -> Unit,
) {
    textFields(state, actions)
    item(key = "location") { LocationItem(state.place, state.showErrors, R.string.save_needs_location) { actions.pickLocation(FormPart.Memory) } }
    item(key = "date_header") { SectionHeader(R.string.date_and_time_heading) }
    item(key = "date") {
        Surface(
            Modifier.padding(horizontal = PAGE_MARGIN),
            shape = ListItemDefaults.segmentedShapes(0, 1).shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            DateRows(FormPart.Memory, state.start, state.end, state.isAllDay, actions, onPick)
        }
    }
    item(key = "color") {
        MarkerColorCard(state.color, state.colorExpanded, presets, enabled = !state.isSaving, part = FormPart.Memory, actions)
    }
    item(key = "fragments_header") { SectionHeader(R.string.memory_fragments) }
    items(state.fragments, key = { it.localId }) { fragment ->
        FragmentCard(fragment, state.showErrors, presets, !state.isSaving, actions, onPick)
    }
    item(key = "add_fragment") {
        OutlinedButton(
            onClick = actions::addFragment,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_MARGIN),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.add_fragment))
        }
    }
    mediaSection(state, actions)
}
