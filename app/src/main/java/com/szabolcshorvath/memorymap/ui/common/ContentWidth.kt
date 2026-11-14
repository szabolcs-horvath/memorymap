package com.szabolcshorvath.memorymap.ui.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a page's content gets. A wider window (a phone turned sideways, a tablet) centres it, so lines stay short
 * enough to read and controls stay near their labels, instead of stretching across the screen.
 */
val MAX_CONTENT_WIDTH = 720.dp

/** The room on each side of a page's content in a window [width] wide: none until it is wider than [MAX_CONTENT_WIDTH]. */
fun sideRoom(width: Dp): Dp = ((width - MAX_CONTENT_WIDTH) / 2).coerceAtLeast(0.dp)
