package com.szabolcshorvath.memorymap.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hsv_presets")
data class HSVPreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
    val order: Int? = null
)
