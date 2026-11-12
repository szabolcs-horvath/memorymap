package com.szabolcshorvath.memorymap.ui.memory

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.util.Coordinates
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState

// The UI tests find these elements by these names (screens/MemoryScreen), the names the View version's ids had.
private const val TAG_PAGE = "memory_page"
private const val TAG_TITLE = "tv_title"
private const val TAG_DESCRIPTION = "tv_description"
private const val TAG_DATE = "tv_date"
private const val TAG_LOCATION = "tv_location"
private const val TAG_FRAGMENT = "fragmentCard"
private const val TAG_FRAGMENT_DATES = "tv_time"
private const val TAG_FRAGMENT_COLOR = "colorIndicator"

private const val GRID_COLUMNS = 3
private const val KEY_MEDIA = "media_"
private const val KEY_FRAGMENT = "fragment_"

/** Below this height a window is compact (Material's window size classes): the top app bar is a small one. */
private val COMPACT_HEIGHT = 480.dp
private val PAGE_MARGIN = 16.dp

/** The gap between a segmented list's items and between the photo tiles: Material 3 Expressive's, as on the Timeline. */
internal val GAP = 4.dp
private val COLOR_STRIP_WIDTH = 8.dp

/** What the user can do on a memory's page. What needs an activity, or asks first, is the route's to do. */
interface MemoryActions {
    fun back()
    fun edit()

    /** Deletes the memory, after asking. */
    fun delete()
    fun showOnMap()
    fun showOnTimeline()
    fun showFragmentOnMap(fragment: MemoryFragment)

    /** Opens [item] in the media viewer, or the sheet that fixes it if it needs re-linking. */
    fun openMedia(item: MediaItem)
    fun relinkMedia()
    fun saveMediaOrder(items: List<MediaItem>)
    fun saveFragmentsOrder(fragments: List<MemoryFragment>)
}

/**
 * A memory's page: a large top app bar with its title, Edit and (in a menu) Delete; a card with its description,
 * dates and place, in the memory's marker colour; its fragments; and its photos and videos in a grid. Fragments and
 * photos are reordered by long-pressing and dragging them. A fragment shows on the map when tapped while fragment
 * markers are turned on ([showFragmentMarkers]).
 */
@Composable
fun MemoryContent(state: MemoryUiState, showFragmentMarkers: Boolean, actions: MemoryActions) {
    // On a short window (a phone turned sideways) the large title would take a third of the screen: a small bar, which
    // stays put. Each keeps its own scroll state.
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
            .background(MaterialTheme.colorScheme.surface)
            // The top bar keeps clear of the status bar; in landscape the page keeps clear of the sides and the bottom.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .semantics { testTagsAsResourceId = true }
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        MemoryTopBar(state.group?.title.orEmpty(), compact, scrollBehavior, actions)
        state.group?.let { MemoryPage(state, it, showFragmentMarkers, actions) }
    }
}

@Composable
private fun MemoryTopBar(title: String, compact: Boolean, scrollBehavior: TopAppBarScrollBehavior, actions: MemoryActions) {
    val titleText = @Composable { Text(title, Modifier.testTag(TAG_TITLE), maxLines = 2, overflow = TextOverflow.Ellipsis) }
    val navigationIcon = @Composable {
        IconButton(onClick = actions::back) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
        }
    }
    val barActions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = actions::edit) { Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.edit_memory)) }
        MoreMenu(onDelete = actions::delete)
    }
    if (compact) {
        TopAppBar(titleText, navigationIcon = navigationIcon, actions = barActions, scrollBehavior = scrollBehavior)
    } else {
        LargeFlexibleTopAppBar(
            titleText,
            navigationIcon = navigationIcon,
            actions = barActions,
            scrollBehavior = scrollBehavior,
        )
    }
}

/** Delete, rarely used and undone only by the Undo that follows: in a menu, so it isn't tapped by mistake. */
@Composable
private fun MoreMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_actions))
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                DropdownMenuItem(
                    onClick = {
                        open = false
                        onDelete()
                    },
                    text = { Text(stringResource(R.string.delete)) },
                    shape = MenuDefaults.itemShape(0, 1).shape,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                )
            }
        }
    }
}

/**
 * The page under the top app bar: one grid, so the whole page scrolls together and the photos and fragments can be
 * dragged within it. The details and fragments span its width; the photos and videos fill its cells.
 */
@Composable
private fun MemoryPage(state: MemoryUiState, group: MemoryGroup, showFragmentMarkers: Boolean, actions: MemoryActions) {
    // The order shown while dragging, until the stored one comes back.
    var media by remember(state.media) { mutableStateOf(state.media) }
    var fragments by remember(state.fragments) { mutableStateOf(state.fragments) }
    val gridState = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyGridState
        val toKey = to.key as? String ?: return@rememberReorderableLazyGridState
        when {
            fromKey.startsWith(KEY_MEDIA) && toKey.startsWith(KEY_MEDIA) ->
                media = media.moved(fromKey, toKey) { KEY_MEDIA + it.item.id }

            fromKey.startsWith(KEY_FRAGMENT) && toKey.startsWith(KEY_FRAGMENT) ->
                fragments = fragments.moved(fromKey, toKey) { KEY_FRAGMENT + it.id }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_PAGE),
        state = gridState,
        contentPadding = PaddingValues(start = PAGE_MARGIN, end = PAGE_MARGIN, bottom = PAGE_MARGIN),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        fullWidth("details") { DetailsCard(group, actions) }
        fragmentRows(fragments, reorder, onShowOnMap = actions::showFragmentOnMap.takeIf { showFragmentMarkers }) {
            if (fragments.map { it.id } != state.fragments.map { it.id }) actions.saveFragmentsOrder(fragments)
        }
        if (media.isNotEmpty()) {
            fullWidth("media_header") { SectionHeader(R.string.media) }
            if (state.hasMissingMedia || state.relinking) {
                fullWidth("media_warning") { MissingMediaBanner(state, actions::relinkMedia) }
            }
            mediaTiles(media, reorder, actions::openMedia) {
                if (media.map { it.item.id } != state.media.map { it.item.id }) actions.saveMediaOrder(media.map { it.item })
            }
        }
    }
}

/**
 * The fragments under their header, each spanning the grid. [onShowOnMap] is null while fragment markers are turned
 * off; [onDragStopped] stores the order a drag left.
 */
private fun LazyGridScope.fragmentRows(
    fragments: List<MemoryFragment>,
    reorder: ReorderableLazyGridState,
    onShowOnMap: ((MemoryFragment) -> Unit)?,
    onDragStopped: () -> Unit,
) {
    if (fragments.isEmpty()) return
    fullWidth("fragments_header") { SectionHeader(R.string.memory_fragments) }
    itemsIndexed(fragments, key = { _, fragment -> KEY_FRAGMENT + fragment.id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { i, fragment ->
        ReorderableItem(reorder, key = KEY_FRAGMENT + fragment.id) {
            FragmentRow(
                fragment = fragment,
                index = i,
                count = fragments.size,
                onClick = onShowOnMap?.let { show -> { show(fragment) } },
                modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped),
            )
        }
    }
}

/** The photos and videos, one per cell; [onDragStopped] stores the order a drag left. */
private fun LazyGridScope.mediaTiles(
    media: List<MediaTile>,
    reorder: ReorderableLazyGridState,
    onOpen: (MediaItem) -> Unit,
    onDragStopped: () -> Unit,
) {
    itemsIndexed(media, key = { _, tile -> KEY_MEDIA + tile.item.id }) { _, tile ->
        ReorderableItem(reorder, key = KEY_MEDIA + tile.item.id) {
            MediaTileView(
                tile = tile,
                onClick = { onOpen(tile.item) },
                modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped),
            )
        }
    }
}

/** This list with the item keyed [from] moved to where the item keyed [to] is. */
private fun <T> List<T>.moved(from: String, to: String, key: (T) -> String): List<T> {
    val fromIndex = indexOfFirst { key(it) == from }
    val toIndex = indexOfFirst { key(it) == to }
    if (fromIndex < 0 || toIndex < 0) return this
    return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

/** The label above a section, lined up with its items' text. */
@Composable
private fun SectionHeader(text: Int) {
    Text(
        text = stringResource(text),
        modifier = Modifier.padding(start = PAGE_MARGIN, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The memory's details: its description, dates and place, and buttons that show it on the map and the timeline. A strip
 * along its left edge has the memory's marker colour, as its card on the Timeline does.
 */
@Composable
private fun DetailsCard(group: MemoryGroup, actions: MemoryActions) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(COLOR_STRIP_WIDTH)
                    .fillMaxHeight()
                    .background(Color(group.markerColor()))
            )
            Column(Modifier.padding(16.dp)) {
                group.description?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, Modifier.testTag(TAG_DESCRIPTION), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                }
                InfoLine(R.drawable.ic_calendar_month, group.getFormattedDate(context).orEmpty(), TAG_DATE)
                Spacer(Modifier.height(4.dp))
                InfoLine(R.drawable.ic_location_on, placeOf(group), TAG_LOCATION)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalButton(R.drawable.ic_map, R.string.show_on_map, actions::showOnMap)
                    TonalButton(R.drawable.ic_view_agenda, R.string.show_on_timeline, actions::showOnTimeline)
                }
            }
        }
    }
}

/** The place's name with its address, either one alone, or the coordinates if it has neither. */
@Composable
private fun placeOf(group: MemoryGroup): String {
    val context = LocalContext.current
    val name = group.placeName?.takeIf { it.isNotEmpty() }
    val address = group.address?.takeIf { it.isNotEmpty() }
    return when {
        name != null && address != null -> stringResource(R.string.place_and_address, name, address)
        name != null -> name
        address != null -> address
        else -> Coordinates.format(context, group.latitude, group.longitude)
    }
}

@Composable
private fun InfoLine(@DrawableRes icon: Int, text: String, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            Modifier.testTag(tag),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TonalButton(@DrawableRes icon: Int, label: Int, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(label))
    }
}

/**
 * A fragment: its place, its own dates if it has them, and a bar in its marker colour. [onClick] shows it on the map;
 * it is null while fragment markers are turned off, as the map wouldn't show it.
 */
@Composable
private fun FragmentRow(
    fragment: MemoryFragment,
    index: Int,
    count: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val place = fragment.placeName?.takeIf { it.isNotEmpty() }
        ?: Coordinates.format(context, fragment.latitude, fragment.longitude)
    val dates = fragment.getFormattedDate(context)
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val supporting = dates?.let { @Composable { Text(it, Modifier.testTag(TAG_FRAGMENT_DATES)) } }
    val headline = @Composable { Text(place, Modifier.testTag(TAG_LOCATION)) }
    // The marker colour runs down the row's left edge, as on the details card, clipped to the row's corners.
    Box(
        modifier
            .clip(shapes.shape)
            .testTag(TAG_FRAGMENT)
    ) {
        if (onClick != null) {
            SegmentedListItem(
                onClick = onClick,
                shapes = shapes,
                supportingContent = supporting,
                trailingContent = {
                    Icon(painterResource(R.drawable.ic_map), stringResource(R.string.show_on_map))
                },
                colors = colors,
                content = headline,
            )
        } else {
            SegmentedListItem(
                shapes = shapes,
                supportingContent = supporting,
                colors = colors,
                content = headline,
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .wrapContentWidth(Alignment.Start)
                .width(COLOR_STRIP_WIDTH)
                .background(Color(fragment.markerColor()))
                .testTag(TAG_FRAGMENT_COLOR)
        )
    }
}
