package dev.nelson.ridewatch

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View

/** Distance plot only: horizontal position does not imply a measured traffic lane. */
class RadarView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bike = requireNotNull(context.getDrawable(R.drawable.ic_bike)).mutate()
    private val car = requireNotNull(context.getDrawable(R.drawable.ic_car)).mutate()
    private var health = "DISCONNECTED"
    private var meters = intArrayOf()
    private var threats = intArrayOf()
    private var accent = Color.GRAY
    private var group = false
    private val density = resources.displayMetrics.density

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Radar off. No live vehicle data."
    }

    fun update(
        health: String,
        meters: IntArray,
        threats: IntArray,
        accent: Int,
        group: Boolean,
        demo: Boolean,
    ) {
        this.health = health
        this.meters = if (health == "LIVE") meters.copyOf() else intArrayOf()
        this.threats = threats.copyOf()
        this.accent = accent
        this.group = group
        contentDescription =
            (if (demo) "Simulation. " else "") +
                (if (group) "Host radar. " else "") +
                "$health. ${this.meters.size} vehicles. Distances in meters: ${this.meters.contentToString()}"
        invalidate()
    }

    private fun d(value: Int): Float = value * density

    private fun label(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Int,
        color: Int,
        align: Paint.Align,
    ) {
        paint.color = color
        paint.textSize = d(size)
        paint.textAlign = align
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText(text, x, y, paint)
    }

    private fun icon(canvas: Canvas, icon: Drawable, x: Float, y: Float, size: Float, color: Int) {
        icon.setTint(color)
        icon.setBounds(
            (x - size / 2).toInt(),
            (y - size / 2).toInt(),
            (x + size / 2).toInt(),
            (y + size / 2).toInt(),
        )
        icon.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val center = w * .53f
        if (h < d(90)) return
        val top = d(68)
        val bottom = h - d(34)
        val span = maxOf(d(20), bottom - top)
        val muted = Color.rgb(137, 155, 165)
        val line = Color.rgb(46, 60, 69)
        paint.color = accent
        canvas.drawRect(0f, d(12), d(4), h - d(10), paint)
        canvas.drawRect(w - d(4), d(12), w, h - d(10), paint)
        paint.color = line
        paint.strokeWidth = d(2)
        canvas.drawLine(center - d(43), top, center - d(43), bottom, paint)
        canvas.drawLine(center + d(43), top, center + d(43), bottom, paint)
        for (distance in 40..140 step 50) {
            val y = top + distance / 154f * span
            paint.color = line
            paint.strokeWidth = d(1)
            canvas.drawLine(center - d(49), y, center - d(38), y, paint)
            label(canvas, "$distance m", center - d(58), y + d(4), 12, muted, Paint.Align.RIGHT)
        }
        icon(canvas, bike, center, d(33), d(38), Color.WHITE)
        label(
            canvas,
            if (group) "HOST" else "YOU",
            center + d(40),
            d(37),
            11,
            muted,
            Paint.Align.LEFT,
        )
        if (meters.isNotEmpty()) {
            label(
                canvas,
                "${meters.minOrNull() ?: 0} m",
                d(16),
                d(30),
                25,
                Color.WHITE,
                Paint.Align.LEFT,
            )
            label(canvas, "NEAREST", d(16), d(48), 10, muted, Paint.Align.LEFT)
            // Draw farthest first so close detections remain visible when icons overlap.
            for (i in meters.indices.sortedByDescending { meters[it] }) {
                val y = top + meters[i].coerceIn(0, 154) / 154f * span
                val color =
                    if (threats.getOrNull(i) == 2) Color.rgb(255, 104, 94)
                    else Color.rgb(255, 196, 85)
                paint.color = Color.rgb(9, 13, 16)
                canvas.drawRoundRect(
                    center - d(22),
                    y - d(22),
                    center + d(22),
                    y + d(22),
                    d(8),
                    d(8),
                    paint,
                )
                icon(canvas, car, center, y, d(37), color)
            }
        } else
            label(
                canvas,
                if (health == "LIVE") "No vehicles detected" else "No live radar data",
                center,
                top + span * .5f,
                15,
                muted,
                Paint.Align.CENTER,
            )
        label(
            canvas,
            if (meters.isNotEmpty()) "${meters.size} VEHICLES BEHIND" else "DISTANCE BEHIND",
            w / 2,
            h - d(10),
            11,
            muted,
            Paint.Align.CENTER,
        )
    }
}
