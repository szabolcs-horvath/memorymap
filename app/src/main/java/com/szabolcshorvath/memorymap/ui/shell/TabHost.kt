package com.szabolcshorvath.memorymap.ui.shell

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner

// Material's fade through: the old tab fades out quickly, then the new one fades in while it grows to its size.
private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 210
private const val FADE_IN_START_SCALE = 0.92f

/**
 * The tabs, each kept as it was left. A tab is composed the first time it is shown ([visited]) and stays composed
 * while another tab shows, so it keeps its state and what it built (the map isn't rebuilt): only [selected] is drawn,
 * and the others are neither drawn nor laid out. A hidden tab is paused (its lifecycle is capped at `STARTED`): it
 * keeps collecting its flows, so it stays up to date, while anything the user should see happen (a scroll, a camera
 * move, a message) waits until it is `RESUMED`. Its Back handlers are turned off while it is hidden. While a screen is
 * shown over the tabs ([covered]), the selected tab is paused and hidden too. Each tab's saved state (`rememberSaveable`)
 * is kept by tab, also for a tab that isn't composed after the activity is recreated.
 */
@Composable
fun TabHost(selected: Tab, visited: List<Tab>, covered: Boolean, modifier: Modifier = Modifier, content: @Composable (Tab) -> Unit) {
    val saveableState = rememberSaveableStateHolder()
    Box(modifier) {
        visited.forEach { tab ->
            key(tab) {
                val shown = tab == selected
                val alpha by animateFloatAsState(
                    targetValue = if (shown) 1f else 0f,
                    animationSpec = if (shown) {
                        tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = LinearOutSlowInEasing)
                    } else {
                        tween(FADE_OUT_MS, easing = FastOutLinearInEasing)
                    },
                    label = "tab_fade",
                )
                val drawn = !covered && (shown || alpha > 0f)
                val active = shown && !covered
                val lifecycle = rememberLifecycleOwner(maxLifecycle = if (active) Lifecycle.State.RESUMED else Lifecycle.State.STARTED)
                val backHandlers = rememberNavigationEventDispatcherOwner(enabled = active)
                Box(
                    Modifier
                        .zIndex(if (shown) 1f else 0f)
                        .drawnOnlyIf(drawn)
                        .fillMaxSize()
                        .graphicsLayer {
                            this.alpha = alpha
                            val scale = if (shown) lerp(FADE_IN_START_SCALE, 1f, alpha) else 1f
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    CompositionLocalProvider(
                        LocalLifecycleOwner provides lifecycle,
                        LocalNavigationEventDispatcherOwner provides backHandlers,
                    ) {
                        saveableState.SaveableStateProvider(tab) { content(tab) }
                    }
                }
            }
        }
    }
}

/**
 * Lays out and places the content only while [drawn]. Content that isn't placed isn't drawn, gets no touches and isn't
 * in the accessibility tree, but stays composed with its state.
 */
private fun Modifier.drawnOnlyIf(drawn: Boolean) = layout { measurable, constraints ->
    if (drawn) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        layout(constraints.minWidth, constraints.minHeight) {}
    }
}
