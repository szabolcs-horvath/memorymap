package com.szabolcshorvath.memorymap.ui.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The narrowest a photo or video tile gets: a phone held upright fits three in a row, and a wider window (a phone turned
 * sideways, a tablet) fits more, smaller ones, instead of three very large tiles.
 */
val MEDIA_TILE_MIN = 110.dp

/** How many tiles of at least [MEDIA_TILE_MIN] fit a row [width] wide, with [gap] between them; never fewer than one. */
fun mediaColumns(width: Dp, gap: Dp): Int = ((width + gap) / (MEDIA_TILE_MIN + gap)).toInt().coerceAtLeast(1)
