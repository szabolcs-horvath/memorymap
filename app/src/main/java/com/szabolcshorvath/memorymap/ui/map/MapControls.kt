package com.szabolcshorvath.memorymap.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.ui.settings.DateFilterMenu
import com.szabolcshorvath.memorymap.util.Coordinates
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import kotlin.math.cos
import kotlin.math.sin

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

/** The colours of the memories shown, as a ring: a slice per colour with its count, and the total in the middle. */
@Composable
internal fun ColorStats(counts: List<ColorCount>) {
    val total = counts.sumOf { it.count }
    val description = pluralStringResource(R.plurals.memories_shown, total, total)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = ELEVATION,
    ) {
        Box(
            Modifier
                .size(STATS_SIZE)
                .padding(STATS_PADDING)
                .testTag(TAG_STATS)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Ring(counts)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total.toString(), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.map_stats_heart), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A slice per colour, a small gap between them, and each slice's count just inside it. */
@Composable
private fun Ring(counts: List<ColorCount>) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface)
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
            val middle = Math.toRadians((start + sweep / 2).toDouble())
            val radius = diameter / 2 - stroke / 2 - LABEL_INSET.toPx()
            val label = measurer.measure(count.toString(), labelStyle)
            drawText(
                label,
                topLeft = Offset(
                    center.x + (radius * cos(middle)).toFloat() - label.size.width / 2,
                    center.y + (radius * sin(middle)).toFloat() - label.size.height / 2,
                ),
            )
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
        shadowElevation = ELEVATION,
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

private const val TAG_DATE_RANGE = "bt_dateRange"
private const val TAG_STATS = "pieChart"
private const val TAG_PLACE = "tv_overlayLocationTitle"
private const val TAG_MEMORY_TITLE = "tv_memoryTitle"
private const val FULL_CIRCLE = 360f
private const val RING_START_DEGREES = -90f
private const val RING_GAP_DEGREES = 3f
private val BAR_HEIGHT = ButtonDefaults.MediumContainerHeight
private val ELEVATION = 3.dp
private val STATS_SIZE = 200.dp
private val STATS_PADDING = 16.dp
private val RING_WIDTH = 24.dp
private val LABEL_INSET = 14.dp
private val PANEL_PADDING = 20.dp
private val PANEL_LIST_HEIGHT = 300.dp
private val LIST_PADDING = 12.dp
private val ROW_GAP = 4.dp
private val COLOR_STRIP_WIDTH = 6.dp
