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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.PinConfig
import com.google.firebase.perf.metrics.AddTrace

object MarkerGenerator {
    private const val TARGET_CONTRAST_FOR_MARKER_COLORS = 2.0
    const val CIRCLE_ANCHOR_U = 0.5f
    const val CIRCLE_ANCHOR_V = 0.5f
    const val PIN_ANCHOR_U = 0.5f
    const val PIN_ANCHOR_V = 1.0f

    private const val CLUSTER_SIZE_DP = 35.0f
    private const val MARKER_SIZE_DP = 25.0f
    private const val CLUSTER_BORDER_WIDTH_DP = 1.0f
    private const val MARKER_BORDER_WIDTH_DP = 1.0f
    private const val CLUSTER_TEXT_SIZE_DP = 14.4f
    private const val MARKER_TEXT_SIZE_DP = 12.0f
    private const val TEXT_OUTLINE_WIDTH_DP = 1.5f
    private const val WIDTH_TO_HEIGHT_SCALING_FACTOR = 1.5f
    private const val DEGREES_360 = 360.0f
    private const val DEGREES_180 = 180.0f
    private const val DEGREES_90 = 90.0f
    private const val TAPERED_CURVE_BOTTOM_Y_FACTOR = 0.7f
    private const val TAPERED_CURVE_CENTER_Y_FACTOR = 0.6f

    private const val CACHE_MAX_SIZE = 50
    private val cache = LruCache<String, BitmapDescriptor>(CACHE_MAX_SIZE)

    @AddTrace(name = "marker_generator_multi_color_bitmap_circle_icon", enabled = true)
    fun multiColorBitmapCircleIcon(colors: List<Int>, count: Int, density: Float): BitmapDescriptor {
        val cacheKey = "circle_${colors.hashCode()}_${count}_$density"
        cache.get(cacheKey)?.let { return it }

        val borderWidth = (CLUSTER_BORDER_WIDTH_DP * density)
        val essentials = Essentials.initClusterEssentials(colors, count, density, borderWidth)
        val outlineWidth = (TEXT_OUTLINE_WIDTH_DP * density)

        drawContent(essentials, borderWidth, outlineWidth)

        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(essentials.bitmap)
        cache.put(cacheKey, bitmapDescriptor)
        return bitmapDescriptor
    }

    @AddTrace(name = "marker_generator_single_color_pin_config_icon", enabled = true)
    fun singleColorPinConfigIcon(@ColorInt color: Int): BitmapDescriptor {
        val contrastColor = ColorUtil.generateColorWithTargetContrast(color, TARGET_CONTRAST_FOR_MARKER_COLORS)
        return BitmapDescriptorFactory.fromPinConfig(
            PinConfig.builder()
                .setBackgroundColor(color)
                .setGlyph(PinConfig.Glyph(contrastColor))
                .setBorderColor(contrastColor)
                .build()
        )
    }

    @AddTrace(name = "marker_generator_multi_color_bitmap_pin_icon", enabled = true)
    fun multiColorBitmapPinIcon(colors: List<Int>, count: Int, density: Float): BitmapDescriptor {
        val cacheKey = "pin_${colors.hashCode()}_${count}_$density"
        cache.get(cacheKey)?.let { return it }

        val borderWidth = (MARKER_BORDER_WIDTH_DP * density)
        val essentials = Essentials.initPinEssentials(colors, count, density, borderWidth)
        val bottomY = essentials.height.toFloat() - borderWidth
        val outlineWidth = (TEXT_OUTLINE_WIDTH_DP * density)

        definePinPath(essentials, bottomY)
        drawContent(essentials, borderWidth, outlineWidth)

        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(essentials.bitmap)
        cache.put(cacheKey, bitmapDescriptor)
        return bitmapDescriptor
    }

    private fun definePinPath(essentials: Essentials, bottomY: Float) {
        // Start from the bottom tip
        essentials.pinPath.moveTo(essentials.centerX, bottomY)

        // Left side curve up to the circle (smooth inward curve)
        essentials.pinPath.cubicTo(
            essentials.centerX,
            bottomY - essentials.radius * TAPERED_CURVE_BOTTOM_Y_FACTOR,
            essentials.centerX - essentials.radius,
            essentials.centerY + essentials.radius * TAPERED_CURVE_CENTER_Y_FACTOR,
            essentials.centerX - essentials.radius,
            essentials.centerY
        )

        // Top circular part
        essentials.pinPath.arcTo(
            essentials.centerX - essentials.radius,
            essentials.centerY - essentials.radius,
            essentials.centerX + essentials.radius,
            essentials.centerY + essentials.radius,
            DEGREES_180,
            DEGREES_180,
            false
        )

        // Right side curve down to the tip (smooth inward curve)
        essentials.pinPath.cubicTo(
            essentials.centerX + essentials.radius,
            essentials.centerY + essentials.radius * TAPERED_CURVE_CENTER_Y_FACTOR,
            essentials.centerX,
            bottomY - essentials.radius * TAPERED_CURVE_BOTTOM_Y_FACTOR,
            essentials.centerX,
            bottomY // End point at tip
        )
        essentials.pinPath.close()
    }

    private fun drawContent(essentials: Essentials, borderWidth: Float, outlineWidth: Float) {
        drawSegments(essentials)
        drawBorder(essentials, borderWidth)
        val textY = drawText(essentials)
        drawTextOutline(essentials, outlineWidth, textY)
        drawTextFill(essentials, textY)
    }

    private fun drawSegments(essentials: Essentials) {
        essentials.canvas.withClip(essentials.pinPath) {
            if (essentials.colors.isNotEmpty()) {
                val angleStep = DEGREES_360 / essentials.colors.size
                val rect = RectF(
                    essentials.centerX - essentials.height,
                    essentials.centerY - essentials.height,
                    essentials.centerX + essentials.height,
                    essentials.centerY + essentials.height
                )

                for (i in essentials.colors.indices) {
                    essentials.paint.color = essentials.colors[i]
                    drawArc(rect, i * angleStep - DEGREES_90, angleStep, true, essentials.paint)
                }
            } else {
                essentials.paint.color = Color.GRAY
                drawPath(essentials.pinPath, essentials.paint)
            }
        }
    }

    private fun drawBorder(essentials: Essentials, borderWidth: Float) {
        essentials.paint.color = Color.BLACK
        essentials.paint.style = Paint.Style.STROKE
        essentials.paint.strokeWidth = borderWidth
        essentials.canvas.drawPath(essentials.pinPath, essentials.paint)
    }

    private fun drawText(essentials: Essentials): Float {
        essentials.paint.textSize = essentials.textSize
        essentials.paint.textAlign = Paint.Align.CENTER

        val textBounds = Rect()
        essentials.paint.getTextBounds(essentials.text, 0, essentials.text.length, textBounds)
        val textY = essentials.centerY - textBounds.exactCenterY()
        return textY
    }

    private fun drawTextOutline(essentials: Essentials, outlineWidth: Float, textY: Float) {
        essentials.paint.style = Paint.Style.STROKE
        essentials.paint.strokeWidth = outlineWidth * 2.0f
        essentials.paint.color = Color.BLACK
        essentials.paint.strokeJoin = Paint.Join.ROUND
        essentials.text.let {
            essentials.canvas.drawText(it, essentials.centerX, textY, essentials.paint)
        }
    }

    private fun drawTextFill(essentials: Essentials, textY: Float) {
        essentials.paint.style = Paint.Style.FILL
        essentials.paint.color = Color.WHITE
        essentials.text.let {
            essentials.canvas.drawText(it, essentials.centerX, textY, essentials.paint)
        }
    }

    private data class Essentials(
        val width: Int,
        val height: Int,
        val density: Float,
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
            fun initClusterEssentials(colors: List<Int>, count: Int, density: Float, borderWidth: Float): Essentials {
                val size = ((CLUSTER_SIZE_DP * density) + (2.0f * borderWidth)).toInt()
                val center = size / 2.0f
                val radius = center - (borderWidth / 2.0f)

                val bitmap = createBitmap(size, size)
                val canvas = Canvas(bitmap)
                val path = Path().apply {
                    addCircle(center, center, radius, Path.Direction.CW)
                }

                return Essentials(
                    width = size,
                    height = size,
                    density = density,
                    centerX = center,
                    centerY = center,
                    radius = radius,
                    bitmap = bitmap,
                    canvas = canvas,
                    pinPath = path,
                    colors = colors,
                    text = count.toString(),
                    textSize = CLUSTER_TEXT_SIZE_DP * density,
                    paint = Paint(Paint.ANTI_ALIAS_FLAG)
                )
            }

            fun initPinEssentials(colors: List<Int>, count: Int, density: Float, borderWidth: Float): Essentials {
                val width = (MARKER_SIZE_DP * density).toInt()
                val height = (width * WIDTH_TO_HEIGHT_SCALING_FACTOR).toInt()
                val centerX = width / 2.0f
                val centerY = width / 2.0f
                val radius = (width / 2.0f) - (borderWidth / 2.0f)
                val bitmap = createBitmap(width, height)
                val canvas = Canvas(bitmap)
                val pinPath = Path()
                val text = count.toString()
                val textSize = (MARKER_TEXT_SIZE_DP * density)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                return Essentials(
                    width = width,
                    height = height,
                    density = density,
                    centerX = centerX,
                    centerY = centerY,
                    radius = radius,
                    bitmap = bitmap,
                    canvas = canvas,
                    pinPath = pinPath,
                    colors = colors,
                    text = text,
                    textSize = textSize,
                    paint = paint
                )
            }
        }
    }
}
