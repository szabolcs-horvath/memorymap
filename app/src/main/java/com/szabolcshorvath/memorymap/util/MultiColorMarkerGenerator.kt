package com.szabolcshorvath.memorymap.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import kotlin.math.cos
import kotlin.math.sin

object MultiColorMarkerGenerator {

    private const val MARKER_SIZE_DP = 30.0f
    private const val BORDER_WIDTH_DP = 1.0f
    private const val TEXT_SIZE_SP = 14.0f
    private const val TEXT_OUTLINE_WIDTH_DP = 1.5f

    /**
     * Generates a pin with a simple triangle tail.
     */
    fun generate(
        colors: List<Int>,
        count: Int,
        density: Float
    ): Bitmap {
        val markerSize = (MARKER_SIZE_DP * density).toInt()
        val borderWidth = (BORDER_WIDTH_DP * density)
        val textSize = (TEXT_SIZE_SP * density)
        val outlineWidth = (TEXT_OUTLINE_WIDTH_DP * density)

        val width = markerSize
        val height = (markerSize * 1.4f).toInt()

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val centerX = width / 2f
        val centerY = markerSize / 2f
        val radius = (markerSize / 2f) - borderWidth
        val r = radius + borderWidth / 2

        val pinPath = Path()
        pinPath.addCircle(centerX, centerY, r, Path.Direction.CW)

        val tailPath = Path()
        val tailAngle = 40f
        val sinAngle = sin(Math.toRadians(tailAngle.toDouble())).toFloat()
        val cosAngle = cos(Math.toRadians(tailAngle.toDouble())).toFloat()

        tailPath.moveTo(centerX - r * cosAngle, centerY + r * sinAngle)
        tailPath.lineTo(centerX, height.toFloat() - borderWidth)
        tailPath.lineTo(centerX + r * cosAngle, centerY + r * sinAngle)
        tailPath.close()

        pinPath.op(tailPath, Path.Op.UNION)

        drawMarkerContent(
            canvas,
            pinPath,
            colors,
            count,
            centerX,
            centerY,
            height,
            borderWidth,
            textSize,
            outlineWidth
        )

        return bitmap
    }

    /**
     * Generates a pin with a tapered, smooth tail resembling the Google Maps pin shape.
     */
    fun generateTapered(
        colors: List<Int>,
        count: Int,
        density: Float
    ): Bitmap {
        val markerSize = (MARKER_SIZE_DP * density).toInt()
        val borderWidth = (BORDER_WIDTH_DP * density)
        val textSize = (TEXT_SIZE_SP * density)
        val outlineWidth = (TEXT_OUTLINE_WIDTH_DP * density)

        val width = markerSize
        val height = (markerSize * 1.5f).toInt()

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val centerX = width / 2f
        val centerY = markerSize / 2f
        val radius = (markerSize / 2f) - borderWidth
        val r = radius + borderWidth / 2

        val pinPath = Path()
        val bottomY = height.toFloat() - borderWidth

        // Start from the bottom tip
        pinPath.moveTo(centerX, bottomY)

        // Left side curve up to the circle (smooth inward curve)
        pinPath.cubicTo(
            centerX, bottomY - r * 0.7f,      // CP1: Pulls up from the tip
            centerX - r, centerY + r * 0.6f,  // CP2: Pulls in towards the circle
            centerX - r, centerY              // End point at circle edge
        )

        // Top circular part
        pinPath.arcTo(
            centerX - r, centerY - r,
            centerX + r, centerY + r,
            180f, 180f, false
        )

        // Right side curve down to the tip (smooth inward curve)
        pinPath.cubicTo(
            centerX + r, centerY + r * 0.6f,  // CP1: Pulls in towards the circle
            centerX, bottomY - r * 0.7f,      // CP2: Pulls up from the tip
            centerX, bottomY                  // End point at tip
        )
        pinPath.close()

        drawMarkerContent(
            canvas,
            pinPath,
            colors,
            count,
            centerX,
            centerY,
            height,
            borderWidth,
            textSize,
            outlineWidth
        )

        return bitmap
    }

    private fun drawMarkerContent(
        canvas: Canvas,
        pinPath: Path,
        colors: List<Int>,
        count: Int,
        centerX: Float,
        centerY: Float,
        height: Int,
        borderWidth: Float,
        textSize: Float,
        outlineWidth: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw background segments with clipping
        canvas.withClip(pinPath) {
            if (colors.isNotEmpty()) {
                val angleStep = 360f / colors.size
                val rect =
                    RectF(centerX - height, centerY - height, centerX + height, centerY + height)

                for (i in colors.indices) {
                    paint.color = colors[i]
                    drawArc(rect, i * angleStep - 90, angleStep, true, paint)
                }
            } else {
                paint.color = Color.GRAY
                drawPath(pinPath, paint)
            }
        }

        // Draw border
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderWidth
        canvas.drawPath(pinPath, paint)

        // Draw text with outline
        val text = count.toString()
        paint.textSize = textSize
        paint.textAlign = Paint.Align.CENTER

        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val textY = centerY - textBounds.exactCenterY()

        // 1. Draw outline
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = outlineWidth * 2
        paint.color = Color.BLACK
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawText(text, centerX, textY, paint)

        // 2. Draw fill
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawText(text, centerX, textY, paint)
    }
}
