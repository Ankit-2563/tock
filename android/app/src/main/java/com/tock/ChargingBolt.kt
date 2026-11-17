package com.tock

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Custom Nothing-style dot-matrix charging lightning bolt.
 * Recreated with mathematical precision from charging-icon-theme-1svg.svg.
 * Exactly 27 circles.
 */
object ChargingBolt {
    const val SVG_WIDTH = 841f
    const val SVG_HEIGHT = 1080f

    data class Circle(val cx: Float, val cy: Float, val r: Float)

    val circles = listOf(
        Circle(539.998f, 59.9905f, 59.9905f),
        Circle(539.998f, 179.78f, 59.9905f),
        Circle(539.998f, 299.998f, 59.9905f),
        Circle(539.998f, 419.787f, 59.9905f),
        Circle(539.998f, 539.998f, 59.9905f),
        Circle(539.998f, 659.795f, 59.9905f),
        Circle(539.998f, 780.006f, 59.9905f),
        Circle(420.209f, 179.78f, 59.9905f),
        Circle(420.209f, 299.998f, 59.9905f),
        Circle(420.209f, 419.787f, 59.9905f),
        Circle(420.209f, 539.998f, 59.9905f),
        Circle(420.209f, 659.795f, 59.9905f),
        Circle(420.209f, 780.006f, 59.9905f),
        Circle(420.209f, 899.795f, 59.9905f),
        Circle(180.201f, 419.787f, 59.9905f),
        Circle(660.025f, 660.025f, 59.9905f),
        Circle(180.201f, 539.998f, 59.9905f),
        Circle(660.025f, 539.814f, 59.9905f),
        Circle(59.9905f, 539.998f, 59.9905f),
        Circle(780.236f, 539.814f, 59.9905f),
        Circle(299.998f, 299.998f, 59.9905f),
        Circle(299.998f, 419.787f, 59.9905f),
        Circle(299.998f, 539.998f, 59.9905f),
        Circle(299.998f, 659.795f, 59.9905f),
        Circle(299.998f, 780.006f, 59.9905f),
        Circle(299.998f, 899.795f, 59.9905f),
        Circle(299.998f, 1020.01f, 59.9905f)
    )

    fun draw(canvas: Canvas, left: Float, top: Float, targetHeight: Float, paint: Paint): Float {
        val scale = targetHeight / SVG_HEIGHT
        val width = SVG_WIDTH * scale

        for (c in circles) {
            val cx = left + c.cx * scale
            val cy = top + c.cy * scale
            val r = c.r * scale
            canvas.drawCircle(cx, cy, r, paint)
        }

        return width
    }
}
