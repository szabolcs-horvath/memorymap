package com.szabolcshorvath.memorymap.ui.theme

import android.app.UiModeManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The app's Material 3 theme: its colours ([LightColors], [DarkColors]) for the phone's dark mode and contrast setting,
 * with Compose's own Material 3 shapes and type scale. [dark] uses the dark colours whatever the phone's setting, for
 * a screen that is always dark (the media viewer).
 */
@Composable
fun MemoryMapTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val contrast = phoneContrast()
    val colorScheme = colorScheme(dark, contrast)
    CompositionLocalProvider(LocalSuccess provides if (dark) DarkSuccess else LightSuccess) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/** Green for a finished task (a backup or restore that worked): Material 3 has no colour role for it. */
val MaterialTheme.success: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSuccess.current

private val LocalSuccess = staticCompositionLocalOf { LightSuccess }

/** The colours for the phone's contrast setting, at the levels Material Components used for its overlays. */
private fun colorScheme(dark: Boolean, contrast: Float): ColorScheme = when {
    contrast >= HIGH_CONTRAST -> if (dark) DarkHighContrastColors else LightHighContrastColors
    contrast >= MEDIUM_CONTRAST -> if (dark) DarkMediumContrastColors else LightMediumContrastColors
    else -> if (dark) DarkColors else LightColors
}

/** The phone's contrast setting (from -1 to 1, 0 by default), kept up to date while it changes. */
@Composable
private fun phoneContrast(): Float {
    val context = LocalContext.current
    val uiModes = remember(context) { context.getSystemService(UiModeManager::class.java) }
    var contrast by remember(uiModes) { mutableFloatStateOf(uiModes.contrast) }
    DisposableEffect(uiModes) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        uiModes.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { uiModes.removeContrastChangeListener(listener) }
    }
    return contrast
}

private const val MEDIUM_CONTRAST = 1f / 3
private const val HIGH_CONTRAST = 2f / 3
