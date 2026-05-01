package com.szabolcshorvath.memorymap.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.google.android.gms.maps.model.AdvancedMarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PinConfig
import com.google.firebase.perf.metrics.AddTrace

object MarkerGenerator {
    private const val TARGET_CONTRAST_FOR_MARKER_COLORS = 2.0
    private const val MARKER_ANCHOR_U = 0.5f
    private const val MARKER_ANCHOR_V = 1.0f

    private const val MARKER_SIZE_DP = 25.0f
    private const val BORDER_WIDTH_DP = 1.0f
    private const val TEXT_SIZE_SP = 12.0f
    private const val TEXT_OUTLINE_WIDTH_DP = 1.5f
    private const val WIDTH_TO_HEIGHT_SCALING_FACTOR = 1.5f
    private const val DEGREES_360 = 360.0f
    private const val DEGREES_180 = 180.0f
    private const val DEGREES_90 = 90.0f
    private const val TAPERED_CURVE_BOTTOM_Y_FACTOR = 0.7f
    private const val TAPERED_CURVE_CENTER_Y_FACTOR = 0.6f

    private const val CACHE_MAX_SIZE = 50
    private val cache = LruCache<String, BitmapDescriptor>(CACHE_MAX_SIZE)

    fun advancedMarkerOptions(position: LatLng, title: String, @ColorInt color: Int): AdvancedMarkerOptions {
        return AdvancedMarkerOptions()
            .position(position)
            .title(title)
            .icon(singleColorPinConfigIcon(color))
    }

    fun advancedMarkerOptions(position: LatLng, title: String, @ColorInt colors: List<Int>, count: Int, density: Float): AdvancedMarkerOptions {
        return AdvancedMarkerOptions()
            .position(position)
            .title(title)
            .icon(multiColorBitmapIcon(colors, count, density))
            .anchor(MARKER_ANCHOR_U, MARKER_ANCHOR_V)
    }

    @AddTrace(name = "marker_generator_single_color_pin_config_icon")
    private fun singleColorPinConfigIcon(@ColorInt color: Int): BitmapDescriptor {
        val contrastColor = ColorUtil.generateColorWithTargetContrast(color, TARGET_CONTRAST_FOR_MARKER_COLORS)
        return BitmapDescriptorFactory.fromPinConfig(
            PinConfig.builder()
                .setBackgroundColor(color)
                .setGlyph(PinConfig.Glyph(contrastColor))
                .setBorderColor(contrastColor)
                .build()
        )
    }

    @AddTrace(name = "marker_generator_multi_color_bitmap_icon", enabled = true)
    private fun multiColorBitmapIcon(colors: List<Int>, count: Int, density: Float): BitmapDescriptor {
        val cacheKey = "${colors.hashCode()}_${count}_$density"
        cache.get(cacheKey)?.let { return it }

        val borderWidth = (BORDER_WIDTH_DP * density)
        val pinEssentials = PinEssentials.initPinEssentials(colors, count, density, borderWidth)
        val bottomY = pinEssentials.height.toFloat() - borderWidth
        val outlineWidth = (TEXT_OUTLINE_WIDTH_DP * density)

        definePinPath(pinEssentials, bottomY)
        drawMarkerContent(pinEssentials, borderWidth, outlineWidth)

        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(pinEssentials.bitmap)
        cache.put(cacheKey, bitmapDescriptor)
        return bitmapDescriptor
    }

    private fun definePinPath(pinEssentials: PinEssentials, bottomY: Float) {
        // Start from the bottom tip
        pinEssentials.pinPath.moveTo(pinEssentials.centerX, bottomY)

        // Left side curve up to the circle (smooth inward curve)
        pinEssentials.pinPath.cubicTo(
            pinEssentials.centerX,
            bottomY - pinEssentials.radius * TAPERED_CURVE_BOTTOM_Y_FACTOR,
            pinEssentials.centerX - pinEssentials.radius,
            pinEssentials.centerY + pinEssentials.radius * TAPERED_CURVE_CENTER_Y_FACTOR,
            pinEssentials.centerX - pinEssentials.radius,
            pinEssentials.centerY
        )

        // Top circular part
        pinEssentials.pinPath.arcTo(
            pinEssentials.centerX - pinEssentials.radius,
            pinEssentials.centerY - pinEssentials.radius,
            pinEssentials.centerX + pinEssentials.radius,
            pinEssentials.centerY + pinEssentials.radius,
            DEGREES_180,
            DEGREES_180,
            false
        )

        // Right side curve down to the tip (smooth inward curve)
        pinEssentials.pinPath.cubicTo(
            pinEssentials.centerX + pinEssentials.radius,
            pinEssentials.centerY + pinEssentials.radius * TAPERED_CURVE_CENTER_Y_FACTOR,
            pinEssentials.centerX,
            bottomY - pinEssentials.radius * TAPERED_CURVE_BOTTOM_Y_FACTOR,
            pinEssentials.centerX,
            bottomY // End point at tip
        )
        pinEssentials.pinPath.close()
    }

    private fun drawMarkerContent(pinEssentials: PinEssentials, borderWidth: Float, outlineWidth: Float) {
        drawSegments(pinEssentials)
        drawBorder(pinEssentials, borderWidth)
        val textY = drawText(pinEssentials)
        drawTextOutline(pinEssentials, outlineWidth, textY)
        drawTextFill(pinEssentials, textY)
    }

    private fun drawSegments(pinEssentials: PinEssentials) {
        pinEssentials.canvas.withClip(pinEssentials.pinPath) {
            if (pinEssentials.colors.isNotEmpty()) {
                val angleStep = DEGREES_360 / pinEssentials.colors.size
                val rect = RectF(
                    pinEssentials.centerX - pinEssentials.height,
                    pinEssentials.centerY - pinEssentials.height,
                    pinEssentials.centerX + pinEssentials.height,
                    pinEssentials.centerY + pinEssentials.height
                )

                for (i in pinEssentials.colors.indices) {
                    pinEssentials.paint.color = pinEssentials.colors[i]
                    drawArc(rect, i * angleStep - DEGREES_90, angleStep, true, pinEssentials.paint)
                }
            } else {
                pinEssentials.paint.color = Color.GRAY
                drawPath(pinEssentials.pinPath, pinEssentials.paint)
            }
        }
    }

    private fun drawBorder(pinEssentials: PinEssentials, borderWidth: Float) {
        pinEssentials.paint.color = Color.BLACK
        pinEssentials.paint.style = Paint.Style.STROKE
        pinEssentials.paint.strokeWidth = borderWidth
        pinEssentials.canvas.drawPath(pinEssentials.pinPath, pinEssentials.paint)
    }

    private fun drawText(pinEssentials: PinEssentials): Float {
        pinEssentials.paint.textSize = pinEssentials.textSize
        pinEssentials.paint.textAlign = Paint.Align.CENTER

        val textBounds = Rect()
        pinEssentials.paint.getTextBounds(pinEssentials.text, 0, pinEssentials.text.length, textBounds)
        val textY = pinEssentials.centerY - textBounds.exactCenterY()
        return textY
    }

    private fun drawTextOutline(pinEssentials: PinEssentials, outlineWidth: Float, textY: Float) {
        pinEssentials.paint.style = Paint.Style.STROKE
        pinEssentials.paint.strokeWidth = outlineWidth * 2.0f
        pinEssentials.paint.color = Color.BLACK
        pinEssentials.paint.strokeJoin = Paint.Join.ROUND
        pinEssentials.text.let {
            pinEssentials.canvas.drawText(it, pinEssentials.centerX, textY, pinEssentials.paint)
        }
    }

    private fun drawTextFill(pinEssentials: PinEssentials, textY: Float) {
        pinEssentials.paint.style = Paint.Style.FILL
        pinEssentials.paint.color = Color.WHITE
        pinEssentials.text.let {
            pinEssentials.canvas.drawText(it, pinEssentials.centerX, textY, pinEssentials.paint)
        }
    }

    private data class PinEssentials(
        val width: Int,
        val height: Int,
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val bitmap: Bitmap,
        val canvas: Canvas,
        val pinPath: Path,
        val colors: List<Int>,
        val text: String,
        val textSize: Float,
        val paint: Paint
    ) {
        companion object {
            fun initPinEssentials(colors: List<Int>, count: Int, density: Float, borderWidth: Float): PinEssentials {
                val width = (MARKER_SIZE_DP * density).toInt()
                val height = (width * WIDTH_TO_HEIGHT_SCALING_FACTOR).toInt()
                val centerX = width / 2.0f
                val centerY = width / 2.0f
                val radius = (width / 2.0f) - (borderWidth / 2.0f)
                val bitmap = createBitmap(width, height)
                val canvas = Canvas(bitmap)
                val pinPath = Path()
                val text = count.toString()
                val textSize = (TEXT_SIZE_SP * density)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                return PinEssentials(width, height, centerX, centerY, radius, bitmap, canvas, pinPath, colors, text, textSize, paint)
            }
        }
    }
}
