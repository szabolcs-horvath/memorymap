package com.szabolcshorvath.memorymap.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.ui.color.COLOR_STRIP_WIDTH
import com.szabolcshorvath.memorymap.ui.common.PANEL_ELEVATION
import com.szabolcshorvath.memorymap.ui.common.PANEL_PADDING
import com.szabolcshorvath.memorymap.ui.settings.DateFilterMenu
import com.szabolcshorvath.memorymap.util.Coordinates
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil

/**
 * The date filter as a connected button group: the quick filters' menu, the dates shown (a tap picks others) and the
 * colour statistics. A button stays filled while what it opened is open.
 */
@Composable
internal fun FilterBar(
    state: MapUiState,
    pickingDates: Boolean,
    onPickDates: () -> Unit,
    actions: MapActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = FilledTonalToggleButtonDefaults.colors(
        containerColor = mapControlColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FilledTonalToggleButton(
                checked = menuOpen,
                onCheckedChange = { menuOpen = true },
                modifier = Modifier.heightIn(min = BAR_HEIGHT),
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                colors = colors,
            ) {
                Icon(painterResource(R.drawable.ic_filter_list), stringResource(R.string.map_quick_filters))
            }
            DateFilterMenu(
                expanded = menuOpen,
                selected = state.appliedFilter,
                onDismiss = { menuOpen = false },
                onPick = { option ->
                    menuOpen = false
                    actions.pickQuickFilter(option)
                },
            )
        }
        FilledTonalToggleButton(
            checked = pickingDates,
            onCheckedChange = { onPickDates() },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = BAR_HEIGHT)
                .testTag(TAG_DATE_RANGE),
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            colors = colors,
        ) {
            Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(filterLabel(state), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalToggleButton(
            checked = state.isStatsVisible,
            onCheckedChange = { actions.toggleStats() },
            modifier = Modifier.heightIn(min = BAR_HEIGHT),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = colors,
        ) {
            Icon(painterResource(R.drawable.ic_pie_chart), stringResource(R.string.map_color_stats))
        }
    }
}

/** The filter's name, or the dates picked by hand. */
@Composable
private fun filterLabel(state: MapUiState): String {
    val start = state.filterStart
    val end = state.filterEnd
    return when {
        state.appliedFilter != null -> stringResource(state.appliedFilter.label)
        start == null || end == null -> stringResource(DateFilterOption.DEFAULT_DATE_FILTER_OPTION.label)
        start == end -> DateTimeFormatterUtil.date(start)
        else -> stringResource(R.string.date_range, DateTimeFormatterUtil.date(start), DateTimeFormatterUtil.date(end))
    }
}

/**
 * The colours of the memories shown: a ring with a slice per colour, and under it a legend with each colour's count,
 * largest first, and their total at the bottom. Only the legend scrolls, when the card has less room than it needs.
 */
@Composable
internal fun ColorStats(counts: List<ColorCount>, modifier: Modifier = Modifier) {
    val total = counts.sumOf { it.count }
    Surface(
        modifier = modifier.testTag(TAG_STATS),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = PANEL_ELEVATION,
    ) {
        Column(Modifier.width(STATS_WIDTH).padding(STATS_PADDING)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(RING_SIZE)
                    .testTag(TAG_STATS_RING),
                contentAlignment = Alignment.Center,
            ) {
                Ring(counts)
                // The ring is only a picture of the legend: screen readers read the legend.
                Text(
                    stringResource(R.string.map_stats_heart),
                    Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(STATS_GAP))
            // Two and a half rows at most: a longer legend scrolls, and the half row cut off at its edge, faded out, shows
            // there is more. On a short screen it gets what is left under the filter bar.
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = LEGEND_ROW_HEIGHT * LEGEND_VISIBLE_ROWS)
                    .fadingEdges(scroll)
                    .verticalScroll(scroll)
                    .testTag(TAG_STATS_LEGEND)
            ) {
                // Two colours a row, read left to right: largest first.
                counts.chunked(LEGEND_COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { LegendCell(it, Modifier.weight(1f)) }
                        repeat(LEGEND_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = LEGEND_DIVIDER_PADDING))
            val totalDescription = pluralStringResource(R.plurals.memories_shown, total, total)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = LEGEND_ROW_HEIGHT)
                    .clearAndSetSemantics {
                        contentDescription = totalDescription
                        testTag = TAG_STATS_TOTAL
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.map_stats_total), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(total.toString(), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** A colour of the legend: a dot of it, and next to it how many memories have it. */
@Composable
private fun LegendCell(count: ColorCount, modifier: Modifier) {
    val description = pluralStringResource(R.plurals.memories_count, count.count, count.count)
    Row(
        modifier
            .heightIn(min = LEGEND_ROW_HEIGHT)
            .clearAndSetSemantics {
                contentDescription = description
                testTag = TAG_STATS_COLOR
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(LEGEND_DOT)
                .clip(CircleShape)
                .background(Color(count.color))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Spacer(Modifier.width(LEGEND_DOT_GAP))
        Text(count.count.toString(), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Fades out the top and bottom edges of a list scrolled by [scroll], each only while there is more to scroll to there. */
private fun Modifier.fadingEdges(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FADE_HEIGHT.toPx().coerceAtMost(size.height / 2)
        if (scroll.canScrollBackward) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = fade), blendMode = BlendMode.DstIn)
        }
        if (scroll.canScrollForward) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** A slice per colour, with a small gap between them. */
@Composable
private fun Ring(counts: List<ColorCount>) {
    Canvas(Modifier.fillMaxSize()) {
        val total = counts.sumOf { it.count }
        if (total == 0) return@Canvas
        val stroke = RING_WIDTH.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val gap = if (counts.size > 1) RING_GAP_DEGREES else 0f
        var start = RING_START_DEGREES
        counts.forEach { (color, count) ->
            val sweep = FULL_CIRCLE * count / total
            drawArc(Color(color), start + gap / 2, sweep - gap, useCenter = false, topLeft, Size(diameter, diameter), style = Stroke(stroke))
            start += sweep
        }
    }
}

/** The memories of the tapped pin, under its place's name: tapping one opens it. */
@Composable
internal fun MemoriesPanel(selection: MarkerSelection, onOpen: (Int) -> Unit) {
    val context = LocalContext.current
    val place = selection.items.firstNotNullOfOrNull { it.placeName }
        ?: Coordinates.format(context, selection.position.latitude, selection.position.longitude)
    val listState = rememberLazyListState()
    LaunchedEffect(selection) {
        val index = selection.items.indexOfFirst { it.groupId == selection.selectedId }
        if (index >= 0) listState.scrollToItem(index)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = PANEL_ELEVATION,
    ) {
        Column(Modifier.padding(vertical = PANEL_PADDING)) {
            Text(
                place,
                modifier = Modifier
                    .padding(horizontal = PANEL_PADDING)
                    .testTag(TAG_PLACE),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LazyColumn(
                Modifier.heightIn(max = PANEL_LIST_HEIGHT),
                state = listState,
                contentPadding = PaddingValues(start = LIST_PADDING, end = LIST_PADDING, top = LIST_PADDING),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                itemsIndexed(selection.items, key = { _, item -> item.groupId }) { index, item ->
                    MemoryRow(item, index, selection.items.size) { onOpen(item.groupId) }
                }
            }
        }
    }
}

/** A memory as a list row, with its colour as a strip down its left edge. */
@Composable
private fun MemoryRow(item: Markerable, index: Int, count: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    val dates = item.getFormattedDate(context)
    Box(Modifier.clip(shapes.shape)) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            supportingContent = dates?.let { @Composable { Text(it) } },
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Text(item.title.orEmpty(), Modifier.testTag(TAG_MEMORY_TITLE), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .matchParentSize()
                .wrapContentWidth(Alignment.Start)
                .width(COLOR_STRIP_WIDTH)
                .background(Color(item.markerColor()))
        )
    }
}

private const val TAG_DATE_RANGE = "map_date_range"
private const val TAG_STATS = "map_stats"
private const val TAG_STATS_RING = "map_stats_ring"
private const val TAG_STATS_COLOR = "map_stats_color"
private const val TAG_STATS_TOTAL = "map_stats_total"
private const val TAG_STATS_LEGEND = "map_stats_legend"
private const val TAG_PLACE = "map_overlay_place"
private const val TAG_MEMORY_TITLE = "map_overlay_memory_title"
private const val FULL_CIRCLE = 360f
private const val RING_START_DEGREES = -90f
private const val RING_GAP_DEGREES = 3f
private val BAR_HEIGHT = ButtonDefaults.MediumContainerHeight
private val STATS_WIDTH = 160.dp
private val STATS_PADDING = 16.dp
private val STATS_GAP = 12.dp
private val RING_SIZE = 112.dp
private val RING_WIDTH = 18.dp
private val LEGEND_DOT = 16.dp
private val LEGEND_DOT_GAP = 8.dp
private const val LEGEND_COLUMNS = 2
private const val LEGEND_VISIBLE_ROWS = 2.5f
private val FADE_HEIGHT = 16.dp
private val LEGEND_ROW_HEIGHT = 28.dp
private val LEGEND_DIVIDER_PADDING = 4.dp
private val PANEL_LIST_HEIGHT = 300.dp
private val LIST_PADDING = 12.dp
private val ROW_GAP = 4.dp
