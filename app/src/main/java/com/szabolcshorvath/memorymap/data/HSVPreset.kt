package com.szabolcshorvath.memorymap.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.abs

@Entity(tableName = "hsv_presets")
data class HSVPreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
    val order: Int? = null
) {
    /** Whether this preset is the colour [hue], [saturation], [brightness], as the colour sliders set it. */
    fun hasColor(hue: Float, saturation: Float, brightness: Float): Boolean =
        abs(this.hue - hue) < TOLERANCE && abs(this.saturation - saturation) < TOLERANCE && abs(this.brightness - brightness) < TOLERANCE

    private companion object {
        // Far below a slider step, so only float rounding is forgiven.
        const val TOLERANCE = 0.001f
    }
}
