package com.szabolcshorvath.memorymap.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The part of the screen a full-screen map keeps its controls out of: the system bars and the camera cutout, apart
 * from what a layout around the screen handles already (the tab bar handles the navigation bar below it). A padding
 * modifier (`windowInsetsPadding`) knows that by itself; the map's `contentPadding` needs the numbers, which is what
 * this is for. Put [trackSafeArea] on the screen's root, and read [mapPadding] inside it.
 */
@Stable
class SafeArea internal constructor() {
    /** What the layouts around the screen handle already. */
    internal var handled by mutableStateOf(WindowInsets(0))

    /**
     * A map's padding: the insets left for this screen, plus [top] and [bottom] for its controls. [top] already
     * includes the top inset when the control measured there keeps clear of the status bar itself ([topInset] false).
     */
    @Composable
    fun mapPadding(top: Dp, bottom: Dp, topInset: Boolean = true): PaddingValues {
        val insets = WindowInsets.safeDrawing.exclude(handled).asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        return PaddingValues(
            start = insets.calculateStartPadding(layoutDirection),
            top = top + if (topInset) insets.calculateTopPadding() else 0.dp,
            end = insets.calculateEndPadding(layoutDirection),
            bottom = bottom + insets.calculateBottomPadding(),
        )
    }
}

@Composable
fun rememberSafeArea(): SafeArea = remember { SafeArea() }

/** Keeps [area] following what the layouts around this one handle already: put it on the screen's root. */
fun Modifier.trackSafeArea(area: SafeArea): Modifier = onConsumedWindowInsetsChanged { area.handled = it }
