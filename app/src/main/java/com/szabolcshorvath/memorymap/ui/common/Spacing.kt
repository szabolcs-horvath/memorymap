package com.szabolcshorvath.memorymap.ui.common

import androidx.compose.ui.unit.dp

// Measures and timings the screens share.

/** The space between a page's edge and its content: Material 3's 16 dp margin. */
internal val PAGE_MARGIN = 16.dp

/** The padding inside a panel that floats over a map (the Map's memory card and statistics, the location picker's card). */
internal val PANEL_PADDING = 20.dp

/** How far a panel that floats over a map stands out from it. */
internal val PANEL_ELEVATION = 3.dp

/** How long a screen's flows keep running after nothing collects them: a turned phone collects them again in time. */
internal const val STATE_FLOW_TIMEOUT_MILLIS = 5000L
