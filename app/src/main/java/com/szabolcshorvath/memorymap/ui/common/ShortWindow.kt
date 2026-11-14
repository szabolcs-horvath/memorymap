package com.szabolcshorvath.memorymap.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Whether the window is short, as a phone turned sideways is (under Material's medium height, 480 dp). A short screen
 * keeps its top app bar small, and a date picker opens for typing the date, since its calendar doesn't fit.
 */
@Composable
fun isShortWindow(): Boolean = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < SHORT_HEIGHT

private val SHORT_HEIGHT = 480.dp
