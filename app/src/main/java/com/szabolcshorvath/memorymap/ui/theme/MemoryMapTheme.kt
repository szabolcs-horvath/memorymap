package com.szabolcshorvath.memorymap.ui.theme

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * The app's Material 3 theme for Compose, taken from the XML theme of [LocalContext] (`AppTheme`): the same colours,
 * including dark mode and the medium and high contrast overlays `MemoryMapApplication` applies, and the same corner
 * sizes. Text uses Compose's own Material 3 type scale. The screens moved to Compose and those still built with Views
 * share one palette while the migration runs (ARCH-10).
 */
@Composable
fun MemoryMapTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // The configuration changes with dark mode; the colours have to be read again then.
    val configuration = LocalConfiguration.current
    val colorScheme = remember(context, configuration) { context.colorScheme() }
    val shapes = remember(context, configuration) { context.shapes() }
    MaterialTheme(colorScheme = colorScheme, shapes = shapes, content = content)
}

private fun Context.colorScheme(): ColorScheme {
    fun color(@AttrRes attr: Int) = Color(MaterialColors.getColor(this, attr, javaClass.simpleName))
    return ColorScheme(
        primary = color(AppCompatR.attr.colorPrimary),
        onPrimary = color(MaterialR.attr.colorOnPrimary),
        primaryContainer = color(MaterialR.attr.colorPrimaryContainer),
        onPrimaryContainer = color(MaterialR.attr.colorOnPrimaryContainer),
        inversePrimary = color(MaterialR.attr.colorPrimaryInverse),
        secondary = color(MaterialR.attr.colorSecondary),
        onSecondary = color(MaterialR.attr.colorOnSecondary),
        secondaryContainer = color(MaterialR.attr.colorSecondaryContainer),
        onSecondaryContainer = color(MaterialR.attr.colorOnSecondaryContainer),
        tertiary = color(MaterialR.attr.colorTertiary),
        onTertiary = color(MaterialR.attr.colorOnTertiary),
        tertiaryContainer = color(MaterialR.attr.colorTertiaryContainer),
        onTertiaryContainer = color(MaterialR.attr.colorOnTertiaryContainer),
        background = color(android.R.attr.colorBackground),
        onBackground = color(MaterialR.attr.colorOnBackground),
        surface = color(MaterialR.attr.colorSurface),
        onSurface = color(MaterialR.attr.colorOnSurface),
        surfaceVariant = color(MaterialR.attr.colorSurfaceVariant),
        onSurfaceVariant = color(MaterialR.attr.colorOnSurfaceVariant),
        surfaceTint = color(AppCompatR.attr.colorPrimary),
        inverseSurface = color(MaterialR.attr.colorSurfaceInverse),
        inverseOnSurface = color(MaterialR.attr.colorOnSurfaceInverse),
        error = color(AppCompatR.attr.colorError),
        onError = color(MaterialR.attr.colorOnError),
        errorContainer = color(MaterialR.attr.colorErrorContainer),
        onErrorContainer = color(MaterialR.attr.colorOnErrorContainer),
        outline = color(MaterialR.attr.colorOutline),
        outlineVariant = color(MaterialR.attr.colorOutlineVariant),
        scrim = Color.Black,
        surfaceBright = color(MaterialR.attr.colorSurfaceBright),
        surfaceContainer = color(MaterialR.attr.colorSurfaceContainer),
        surfaceContainerHigh = color(MaterialR.attr.colorSurfaceContainerHigh),
        surfaceContainerHighest = color(MaterialR.attr.colorSurfaceContainerHighest),
        surfaceContainerLow = color(MaterialR.attr.colorSurfaceContainerLow),
        surfaceContainerLowest = color(MaterialR.attr.colorSurfaceContainerLowest),
        surfaceDim = color(MaterialR.attr.colorSurfaceDim),
        primaryFixed = color(MaterialR.attr.colorPrimaryFixed),
        primaryFixedDim = color(MaterialR.attr.colorPrimaryFixedDim),
        onPrimaryFixed = color(MaterialR.attr.colorOnPrimaryFixed),
        onPrimaryFixedVariant = color(MaterialR.attr.colorOnPrimaryFixedVariant),
        secondaryFixed = color(MaterialR.attr.colorSecondaryFixed),
        secondaryFixedDim = color(MaterialR.attr.colorSecondaryFixedDim),
        onSecondaryFixed = color(MaterialR.attr.colorOnSecondaryFixed),
        onSecondaryFixedVariant = color(MaterialR.attr.colorOnSecondaryFixedVariant),
        tertiaryFixed = color(MaterialR.attr.colorTertiaryFixed),
        tertiaryFixedDim = color(MaterialR.attr.colorTertiaryFixedDim),
        onTertiaryFixed = color(MaterialR.attr.colorOnTertiaryFixed),
        onTertiaryFixedVariant = color(MaterialR.attr.colorOnTertiaryFixedVariant),
    )
}

private fun Context.shapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(cornerSize(MaterialR.attr.shapeCornerSizeExtraSmall)),
    small = RoundedCornerShape(cornerSize(MaterialR.attr.shapeCornerSizeSmall)),
    medium = RoundedCornerShape(cornerSize(MaterialR.attr.shapeCornerSizeMedium)),
    large = RoundedCornerShape(cornerSize(MaterialR.attr.shapeCornerSizeLarge)),
    extraLarge = RoundedCornerShape(cornerSize(MaterialR.attr.shapeCornerSizeExtraLarge)),
)

/** A corner size attribute of the theme (a dimension), in dp. */
private fun Context.cornerSize(@AttrRes attr: Int): Dp {
    val value = TypedValue()
    check(theme.resolveAttribute(attr, value, true)) { "The theme has no corner size ${resources.getResourceName(attr)}" }
    return (value.getDimension(resources.displayMetrics) / resources.displayMetrics.density).dp
}
