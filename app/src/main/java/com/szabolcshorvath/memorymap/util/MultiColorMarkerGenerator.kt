package com.szabolcshorvath.memorymap.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

object MultiColorMarkerGenerator {
    private const val CACHE_MAX_SIZE = 50
    private const val MARKER_SIZE_DP = 30.0f
    private const val BORDER_WIDTH_DP = 1.0f
    private const val TEXT_SIZE_SP = 14.0f
    private const val TEXT_OUTLINE_WIDTH_DP = 1.5f
    private const val WIDTH_TO_HEIGHT_SCALING_FACTOR = 1.5f
    private const val DEGREES_360 = 360.0f
    private const val DEGREES_180 = 180.0f
    private const val DEGREES_90 = 90.0f

    private const val TAPERED_CURVE_BOTTOM_Y_FACTOR = 0.7f
    private const val TAPERED_CURVE_CENTER_Y_FACTOR = 0.6f

    private val cache = LruCache<String, Bitmap>(CACHE_MAX_SIZE)

    private data class Essentials(
        val width: Int,
        val height: Int,
        val canvas: Canvas,
        val pinPath: Path,
        val colors: List<Int>,
        val text: String,
        val textSize: Float,
        val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    )

    /**
     * Generates a pin with a tapered, smooth tail resembling the Google Maps pin shape.
     */
    fun generateTapered(colors: List<Int>, count: Int, density: Float): Bitmap {
        val cacheKey = "${colors.hashCode()}_${count}_$density"
        cache.get(cacheKey)?.let { return it }

        val width = (MARKER_SIZE_DP * density).toInt()
        val height = (width * WIDTH_TO_HEIGHT_SCALING_FACTOR).toInt()
        val borderWidth = (BORDER_WIDTH_DP * density)
        val textSize = (TEXT_SIZE_SP * density)
        val outlineWidth = (TEXT_OUTLINE_WIDTH_DP * density)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val centerX = width / 2.0f
        val centerY = width / 2.0f
        val radius = (width / 2.0f) - borderWidth
        val r = radius + borderWidth / 2.0f

        val pinPath = Path()
        val bottomY = height.toFloat() - borderWidth

        // Start from the bottom tip
        pinPath.moveTo(centerX, bottomY)

        // Left side curve up to the circle (smooth inward curve)
        pinPath.cubicTo(
            centerX,
            bottomY - r * TAPERED_CURVE_BOTTOM_Y_FACTOR, // CP1: Pulls up from the tip
            centerX - r,
            centerY + r * TAPERED_CURVE_CENTER_Y_FACTOR, // CP2: Pulls in towards the circle
            centerX - r,
            centerY // End point at circle edge
        )

        // Top circular part
        pinPath.arcTo(
            centerX - r,
            centerY - r,
            centerX + r,
            centerY + r,
            DEGREES_180,
            DEGREES_180,
            false
        )

        // Right side curve down to the tip (smooth inward curve)
        pinPath.cubicTo(
            centerX + r,
            centerY + r * TAPERED_CURVE_CENTER_Y_FACTOR, // CP1: Pulls in towards the circle
            centerX,
            bottomY - r * TAPERED_CURVE_BOTTOM_Y_FACTOR, // CP2: Pulls up from the tip
            centerX,
            bottomY // End point at tip
        )
        pinPath.close()

        drawMarkerContent(
            Essentials(width, height, canvas, pinPath, colors, count.toString(), textSize),
            centerX,
            centerY,
            borderWidth,
            outlineWidth
        )

        cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun drawMarkerContent(
        essentials: Essentials,
        centerX: Float,
        centerY: Float,
        borderWidth: Float,
        outlineWidth: Float
    ) {
        drawSegments(essentials, centerX, centerY)
        drawBorder(essentials, borderWidth)
        val textY = drawText(essentials, centerY)
        drawOutline(essentials, outlineWidth, centerX, textY)
        drawFill(essentials, centerX, textY)
    }

    private fun drawSegments(
        essentials: Essentials,
        centerX: Float,
        centerY: Float
    ) {
        essentials.canvas.withClip(essentials.pinPath) {
            if (essentials.colors.isNotEmpty()) {
                val angleStep = DEGREES_360 / essentials.colors.size
                val rect =
                    RectF(
                        centerX - essentials.height,
                        centerY - essentials.height,
                        centerX + essentials.height,
                        centerY + essentials.height
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
        essentials.paint.color = Color.WHITE
        essentials.paint.style = Paint.Style.STROKE
        essentials.paint.strokeWidth = borderWidth
        essentials.canvas.drawPath(essentials.pinPath, essentials.paint)
    }

    private fun drawText(essentials: Essentials, centerY: Float): Float {
        essentials.paint.textSize = essentials.textSize
        essentials.paint.textAlign = Paint.Align.CENTER

        val textBounds = Rect()
        essentials.paint.getTextBounds(essentials.text, 0, essentials.text.length, textBounds)
        val textY = centerY - textBounds.exactCenterY()
        return textY
    }

    private fun drawOutline(
        essentials: Essentials,
        outlineWidth: Float,
        centerX: Float,
        textY: Float
    ) {
        essentials.paint.style = Paint.Style.STROKE
        essentials.paint.strokeWidth = outlineWidth * 2.0f
        essentials.paint.color = Color.BLACK
        essentials.paint.strokeJoin = Paint.Join.ROUND
        essentials.canvas.drawText(essentials.text, centerX, textY, essentials.paint)
    }

    private fun drawFill(essentials: Essentials, centerX: Float, textY: Float) {
        essentials.paint.style = Paint.Style.FILL
        essentials.paint.color = Color.WHITE
        essentials.canvas.drawText(essentials.text, centerX, textY, essentials.paint)
    }
}
