package com.szabolcshorvath.memorymap.view

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView

/**
 * A MaterialCardView that supports android:maxHeight and forces wrapping behavior.
 *
 * IMPORTANT: This view intentionally forces MeasureSpec.AT_MOST for its height measurement.
 * This ensures that the card always wraps its content (up to maxHeight) and ignores
 * attempts by parent layouts (like ConstraintLayout with vertical bias) to stretch it
 * to a larger exact size.
 */
class MaxHeightCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    private var maxHeight: Int = -1

    init {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.maxHeight), defStyleAttr, 0)
        maxHeight = a.getDimensionPixelSize(0, -1)
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var newHeightMeasureSpec = heightMeasureSpec
        if (maxHeight > 0) {
            val hSize = MeasureSpec.getSize(heightMeasureSpec)

            // We force AT_MOST mode here to ensure the card wraps its content.
            // This prevents the card from stretching to fill available space
            // provided by parent constraints.
            newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(hSize.coerceAtMost(maxHeight), MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }
}
