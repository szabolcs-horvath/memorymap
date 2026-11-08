package com.szabolcshorvath.memorymap.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.fragment.TimelineDay
import com.szabolcshorvath.memorymap.fragment.TimelineUiState
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

// The UI tests find these elements by these names (screens/TimelineScreen), the names the View version's ids had.
private const val TAG_LIST = "rv_timeline"
private const val TAG_EMPTY = "emptyView"
private const val TAG_DAY = "tv_date"
private const val TAG_MEMORY_TITLE = "tv_memoryTitle"
private const val TAG_SEARCH = "et_search"

private const val FLASH_MILLIS = 1000
private const val FLASH_PEAK = 0.5f

/** The gamma Android's `ArgbEvaluator` blends colours in, which the flash copies. */
private const val GAMMA = 2.2f

/**
 * The Timeline tab: a search field that scrolls away with the list, the memories grouped by day, newest first, and a
 * Scroll to top button. [onQueryChange] gets the search as typed (also the one restored after process death), and
 * [onScrollHandled] is called once a Show on Timeline request has been scrolled to.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TimelineContent(
    state: TimelineUiState,
    onQueryChange: (String) -> Unit,
    onMemoryClick: (Int) -> Unit,
    onScrollHandled: (Int) -> Unit,
) {
    val query = rememberTextFieldState()
    LaunchedEffect(query) { snapshotFlow { query.text.toString() }.collect(onQueryChange) }

    val listState = rememberLazyListState()
    // Material's search bar: it scrolls away with the list and comes back as soon as the list scrolls up.
    val searchBarState = rememberSearchBarState()
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    var searchBarHeight by remember { mutableIntStateOf(0) }
    var searchFocused by rememberSaveable { mutableStateOf(false) }
    val flash = remember { FlashState() }
    val scope = rememberCoroutineScope()

    // A hidden tab keeps its list up to date, but scrolls and flashes only once it is shown.
    val shown = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val days = state.days
    LaunchedEffect(state.pendingScrollMemoryId, days, shown) {
        val memoryId = state.pendingScrollMemoryId ?: return@LaunchedEffect
        val index = days?.indexOfMemory(memoryId) ?: -1
        if (!shown || index < 0) return@LaunchedEffect
        listState.scrollToItem(index)
        flash.start(memoryId)
        onScrollHandled(memoryId)
    }

    Box(
        Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        when {
            days == null -> Unit

            days.isEmpty() -> EmptyMessage(state.query)

            // The list scrolls beneath the search bar, and starts below it.
            else -> MemoryList(
                days = days,
                listState = listState,
                flash = flash,
                onMemoryClick = onMemoryClick,
                contentPadding = PaddingValues(top = with(LocalDensity.current) { searchBarHeight.toDp() }),
            )
        }
        TopSearchBar(
            state = searchBarState,
            inputField = { SearchField(query, searchFocused) { searchFocused = it } },
            modifier = Modifier
                .onSizeChanged { searchBarHeight = it.height }
                .padding(horizontal = SEARCH_BAR_MARGIN - TOP_SEARCH_BAR_PADDING),
            windowInsets = WindowInsets(0),
            scrollBehavior = scrollBehavior,
        )
        ScrollToTopButton(
            visible = days?.isNotEmpty() == true && listState.isScrolledPastFirstItem(),
            onClick = {
                scope.launch { listState.animateScrollToItem(0) }
                scope.launch { animate(scrollBehavior.scrollOffset, 0f) { value, _ -> scrollBehavior.scrollOffset = value } }
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** A Show on Timeline flash of memory [memoryId]; [serial] tells two flashes of the same memory apart. */
private data class Flash(val memoryId: Int, val serial: Int)

/** The flash running or about to run, if any. The card it is for plays it, also if it scrolls into view later. */
private class FlashState {
    var current by mutableStateOf<Flash?>(null)
        private set

    fun start(memoryId: Int) {
        current = Flash(memoryId, (current?.serial ?: 0) + 1)
    }

    /** The card played [flash]: forget it, unless a newer one has started meanwhile. */
    fun end(flash: Flash) {
        if (current == flash) current = null
    }
}

/** The position of memory [memoryId] among the list's rows (a day header, then its memories), or -1. */
private fun List<TimelineDay>.indexOfMemory(memoryId: Int): Int {
    var index = 0
    for (day in this) {
        index++ // The day's header.
        val inDay = day.memories.indexOfFirst { it.id == memoryId }
        if (inDay >= 0) return index + inDay
        index += day.memories.size
    }
    return -1
}

@Composable
private fun LazyListState.isScrolledPastFirstItem(): Boolean {
    val scrolled by remember(this) { derivedStateOf { firstVisibleItemIndex > 0 } }
    return scrolled
}

/**
 * The search bar's field. It filters the list below as the user types, so it never opens a separate search view: the
 * field is "expanded" only while it has [focused], and Back (or Search on the keyboard) takes the focus away.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchField(query: TextFieldState, focused: Boolean, onFocusedChange: (Boolean) -> Unit) {
    val focusManager = LocalFocusManager.current
    SearchBarDefaults.InputField(
        state = query,
        onSearch = { focusManager.clearFocus() },
        expanded = focused,
        onExpandedChange = { expanded ->
            onFocusedChange(expanded)
            if (!expanded) focusManager.clearFocus()
        },
        modifier = Modifier
            .onFocusChanged { onFocusedChange(it.hasFocus) }
            .testTag(TAG_SEARCH),
        placeholder = { Text(stringResource(R.string.timeline_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (focused && query.text.isNotEmpty()) {
            {
                IconButton(onClick = { query.clearText() }) {
                    Icon(painterResource(R.drawable.ic_cancel), stringResource(R.string.clear_search))
                }
            }
        } else {
            null
        },
    )
}

/**
 * The margin Material 3 Expressive gives a search bar. (Focused, it would narrow to 12 dp, but `TopSearchBar` in
 * Material3 1.4 widens only into its expanded search view, which this in-place search doesn't use.)
 */
private val SEARCH_BAR_MARGIN = 24.dp

/** The padding `TopSearchBar` keeps around its bar on every side (its internal `SearchBarAsTopBarPadding`). */
private val TOP_SEARCH_BAR_PADDING = 8.dp

@Composable
private fun EmptyMessage(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (query.isBlank()) {
                stringResource(R.string.timeline_empty)
            } else {
                stringResource(R.string.timeline_no_matches, query.trim())
            },
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .testTag(TAG_EMPTY),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MemoryList(
    days: List<TimelineDay>,
    listState: LazyListState,
    flash: FlashState,
    onMemoryClick: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_LIST),
        contentPadding = contentPadding,
    ) {
        for (day in days) {
            item(key = "date_${day.date}", contentType = "day") { DayHeader(day) }
            items(day.memories.size, key = { "memory_${day.memories[it].id}" }, contentType = { "memory" }) { i ->
                val memory = day.memories[i]
                MemoryCard(
                    memory = memory,
                    segment = Segment.of(i, day.memories.size),
                    flash = flash,
                    onClick = { onMemoryClick(memory.id) },
                )
            }
        }
    }
}

/** The label above a day's group of memories, lined up with their text. */
@Composable
private fun DayHeader(day: TimelineDay) {
    Text(
        text = DateTimeFormatterUtil.date(day.date),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = LIST_MARGIN + ITEM_PADDING, end = LIST_MARGIN, top = 16.dp, bottom = 8.dp)
            .testTag(TAG_DAY),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** The list's side margin, and the space inside a list item before its text (Material 3's list item padding). */
private val LIST_MARGIN = 16.dp
private val ITEM_PADDING = 16.dp

/** The gap between the items of one day's group (Material 3 Expressive's segmented list). */
private val SEGMENT_GAP = 2.dp

/** Where a card is among its day's cards: the outer corners of a day's group are round, the inner ones small. */
private enum class Segment(val roundTop: Boolean, val roundBottom: Boolean) {
    SINGLE(true, true),
    FIRST(true, false),
    MIDDLE(false, false),
    LAST(false, true);

    companion object {
        fun of(position: Int, count: Int) = when {
            count == 1 -> SINGLE
            position == 0 -> FIRST
            position == count - 1 -> LAST
            else -> MIDDLE
        }
    }
}

@Composable
private fun MemoryCard(memory: MemoryGroup, segment: Segment, flash: FlashState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // A pressed card rounds all its corners, as Material's segmented list items do.
    val roundness by animateFloatAsState(if (pressed) 1f else 0f, spring(), label = "corners")
    val shape = SegmentedShape(segment, roundness, MaterialTheme.shapes.large, MaterialTheme.shapes.extraSmall)

    val flashProgress = remember { Animatable(0f) }
    val myFlash = flash.current?.takeIf { it.memoryId == memory.id }
    LaunchedEffect(myFlash) {
        if (myFlash == null) return@LaunchedEffect
        flashProgress.snapTo(0f)
        flashProgress.animateTo(1f, tween(FLASH_MILLIS, easing = AccelerateDecelerate))
        flashProgress.snapTo(0f)
        flash.end(myFlash)
    }
    // A filled item, set apart from the next one by a small gap, as Material 3 Expressive's segmented lists are.
    val itemColor = flashColor(flashProgress.value, colors.surfaceContainer, colors.secondaryContainer)

    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        Box(
            Modifier
                .padding(horizontal = LIST_MARGIN)
                .padding(top = if (segment.roundTop) 0.dp else SEGMENT_GAP)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(itemColor)
                .clickable(interactionSource, ripple(), onClick = onClick)
        ) {
            Box(
                Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(Color(memory.markerColor()))
            )
            MemoryText(memory)
        }
    }
}

/** A card's text: the title, the place (its name, else its address), the description and the dates. */
@Composable
private fun MemoryText(memory: MemoryGroup) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = ITEM_PADDING, vertical = 12.dp)) {
        Text(
            text = memory.title,
            modifier = Modifier.testTag(TAG_MEMORY_TITLE),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        val location = memory.placeName?.takeIf { it.isNotEmpty() } ?: memory.address?.takeIf { it.isNotEmpty() }
        if (location != null) {
            Spacer(Modifier.height(2.dp))
            Text(location, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        val description = memory.description
        if (!description.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = memory.getFormattedDate(LocalContext.current).orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * The card's shape: [large] corners on the outer side of its day's group, [small] ones inside, and all [large] as
 * [roundness] goes to 1 (pressed).
 */
private class SegmentedShape(
    private val segment: Segment,
    private val roundness: Float,
    private val large: CornerBasedShape,
    private val small: CornerBasedShape,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val largePx = large.topStart.toPx(size, density)
        val smallPx = small.topStart.toPx(size, density)
        fun corner(round: Boolean) = if (round) largePx else smallPx + (largePx - smallPx) * roundness
        val top = corner(segment.roundTop)
        val bottom = corner(segment.roundBottom)
        return RoundedCornerShape(top, top, bottom, bottom).createOutline(size, layoutDirection, density)
    }
}

/** Android's `AccelerateDecelerateInterpolator`, which the View version's flash ran with. */
private val AccelerateDecelerate = Easing { x -> ((1 + cos((x + 1) * PI)) / 2).toFloat() }

/** The flash's colour at [progress]: from [base] to [peak] and back, blended as Android's `ArgbEvaluator` does. */
private fun flashColor(progress: Float, base: Color, peak: Color): Color = when {
    progress <= 0f || progress >= 1f -> base
    progress <= FLASH_PEAK -> blend(base, peak, progress / FLASH_PEAK)
    else -> blend(peak, base, (progress - FLASH_PEAK) / (1 - FLASH_PEAK))
}

private fun blend(from: Color, to: Color, fraction: Float): Color {
    fun linear(channel: Float) = channel.pow(GAMMA)
    fun mix(a: Float, b: Float) = (linear(a) + (linear(b) - linear(a)) * fraction).pow(1 / GAMMA)
    return Color(
        red = mix(from.red, to.red),
        green = mix(from.green, to.green),
        blue = mix(from.blue, to.blue),
        alpha = from.alpha + (to.alpha - from.alpha) * fraction,
    )
}

@Composable
private fun ScrollToTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(16.dp),
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        FloatingActionButton(onClick = onClick) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.scroll_to_top),
                modifier = Modifier.rotate(UP),
            )
        }
    }
}

/** Turns the right-pointing chevron to point up. */
private const val UP = 270f
