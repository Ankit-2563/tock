package com.boop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

class BoopClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Paints ──────────────────────────────────────────────────
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val periodPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── State ───────────────────────────────────────────────────
    private var theme: ColorTheme = ColorTheme.CLASSIC
    private var use24Hour = true
    private var burnInProtection = true
    private var lowPower = false

    private var batteryPercent = -1
    private var isCharging = false

    // Burn-in shift
    private var burnInOffsetX = 0f
    private var burnInOffsetY = 0f
    private var burnInTick = 0

    // Font
    private val nothingFont: Typeface = Typeface.createFromAsset(context.assets, "fonts/NothingFont5x7.ttf")

    // Gesture detector for opening settings (tap or long press)
    var onOpenSettingsListener: (() -> Unit)? = null
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onOpenSettingsListener?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onOpenSettingsListener?.invoke()
        }
    })

    // Timer
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateBurnIn()
            invalidate()
            // Tick every second (or every 30s in low-power mode)
            handler.postDelayed(this, if (lowPower) 30_000L else 1_000L)
        }
    }

    // Battery receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryPercent = if (level >= 0) (level * 100 / scale) else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL ||
                         plugged != 0
            invalidate()
        }
    }

    init {
        metaPaint.typeface = nothingFont
        periodPaint.typeface = nothingFont
        applyTheme()
    }

    fun setTheme(t: ColorTheme) {
        theme = t
        applyTheme()
        invalidate()
    }

    fun set24Hour(v: Boolean) {
        use24Hour = v
        invalidate()
    }

    fun setBurnInProtection(v: Boolean) {
        burnInProtection = v
        if (!v) {
            burnInOffsetX = 0f
            burnInOffsetY = 0f
        }
        invalidate()
    }

    fun setLowPower(v: Boolean) {
        lowPower = v
        invalidate()
    }

    private fun applyTheme() {
        setBackgroundColor(theme.background)
        digitPaint.color = theme.digitColor
        digitPaint.style = Paint.Style.FILL
        colonPaint.color = theme.colonColor
        colonPaint.style = Paint.Style.FILL
        boltPaint.color = theme.periodColor
        boltPaint.style = Paint.Style.FILL
        metaPaint.color = theme.metaColor
        periodPaint.color = theme.periodColor
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(tickRunnable)
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tickRunnable)
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun updateBurnIn() {
        if (!burnInProtection) {
            burnInOffsetX = 0f
            burnInOffsetY = 0f
            return
        }
        burnInTick++
        val phase = (burnInTick / 60) % 4
        burnInOffsetX = when (phase) { 0 -> 4f; 1 -> -4f; 2 -> 0f; else -> 0f }
        burnInOffsetY = when (phase) { 0 -> 0f; 1 -> 0f; 2 -> 4f; else -> -4f }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(burnInOffsetX, burnInOffsetY)

        val w = width.toFloat()
        val h = height.toFloat()
        val isLandscape = w > h

        val cal = Calendar.getInstance()
        val rawHour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val hour: Int
        val period: String?
        if (use24Hour) {
            hour = rawHour
            period = null
        } else {
            val mod = rawHour % 12
            hour = if (mod == 0) 12 else mod
            period = if (rawHour >= 12) "PM" else "AM"
        }

        val d1 = hour / 10
        val d2 = hour % 10
        val d3 = minute / 10
        val d4 = minute % 10

        // ── Layout calculations with generous side padding (at least 64px/dp) ──
        val density = resources.displayMetrics.density
        val minSidePadding = max(64f, 64f * density)
        val sidePadding = if (isLandscape) max(minSidePadding, w * 0.085f) else max(minSidePadding, w * 0.06f)

        val headerTopPadding = if (isLandscape) max(24f, 24f * density) else max(48f, 48f * density)
        val metaFontSize = max(14f * density, min(w, h) * 0.045f)
        val periodFontSize = max(16f * density, min(w, h) * 0.05f)

        metaPaint.textSize = metaFontSize
        periodPaint.textSize = periodFontSize

        val headerY = headerTopPadding + metaFontSize
        val headerTotalHeight = headerY + 16f * density
        val bottomMargin = 20f * density

        val availableW = w - (sidePadding * 2f)
        val availableH = h - headerTotalHeight - bottomMargin

        val byWidth = availableW / 28.5f
        val byHeight = availableH / 8.5f
        val rawDot = if (isLandscape) min(byWidth, byHeight) else min(availableW / 28.5f, availableH / 12f)

        val dotSize = max(6f * density, rawDot)
        val dotRadius = dotSize / 2f
        val gap = max(2f, dotSize * 0.22f)
        val colonGap = max(6f * density, dotSize * 0.95f)
        val digitGap = max(8f * density, dotSize * 1.05f)

        val digitWidth = 5 * dotSize + 4 * gap
        val digitHeight = 7 * dotSize + 6 * gap

        // Optical column bounds for each digit to ensure perfectly symmetric colon spacing
        val d1LeftCol = getLeftmostCol(d1)
        val d2RightCol = getRightmostCol(d2)
        val d3LeftCol = getLeftmostCol(d3)
        val d4RightCol = getRightmostCol(d4)

        // Raw layout coordinates
        val rawX1 = 0f
        val rawX2 = rawX1 + digitWidth + digitGap
        val d2RightDotX = rawX2 + d2RightCol * (dotSize + gap) + dotRadius

        // Visual distance from d2 dot to colon center, and from colon center to d3 dot
        // This guarantees the spacing on both sides of the colon is EQUAL AT ALL TIMES.
        val colonSpacing = max(10f * density, dotSize * 1.55f)

        val colonCenterX = d2RightDotX + colonSpacing
        val d3LeftDotX = colonCenterX + colonSpacing
        val rawX3 = d3LeftDotX - (d3LeftCol * (dotSize + gap) + dotRadius)
        val rawX4 = rawX3 + digitWidth + digitGap

        val d1LeftDotX = rawX1 + d1LeftCol * (dotSize + gap) + dotRadius
        val d4RightDotX = rawX4 + d4RightCol * (dotSize + gap) + dotRadius

        // Center the full visual clock horizontally
        val totalVisualWidth = d4RightDotX - d1LeftDotX
        val visualStartX = (w - totalVisualWidth) / 2f
        val shiftX = visualStartX - d1LeftDotX

        val x1 = rawX1 + shiftX
        val x2 = rawX2 + shiftX
        val finalColonCenterX = colonCenterX + shiftX
        val x3 = rawX3 + shiftX
        val x4 = rawX4 + shiftX

        // Center digits vertically in available area below header
        val startY = headerTotalHeight + (h - headerTotalHeight - digitHeight) / 2f

        // ── Header (Date, Battery, AM/PM) ───────────────────────
        val days = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val months = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val dayStr = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val monthStr = months[cal.get(Calendar.MONTH)]
        val dateNum = cal.get(Calendar.DAY_OF_MONTH)
        val dateStr = "$dayStr $monthStr $dateNum"

        var leftCurX = visualStartX - dotRadius
        canvas.drawText(dateStr, leftCurX, headerY, metaPaint)
        leftCurX += metaPaint.measureText(dateStr)

        if (batteryPercent >= 0) {
            val dashStr = " - "
            canvas.drawText(dashStr, leftCurX, headerY, metaPaint)
            leftCurX += metaPaint.measureText(dashStr)

            if (isCharging) {
                val boltHeight = metaFontSize * 1.15f
                val boltTop = headerY - metaFontSize * 0.95f
                val boltWidth = ChargingBolt.draw(canvas, leftCurX + 4f, boltTop, boltHeight, boltPaint)
                leftCurX += boltWidth + 10f
            }

            val battStr = "$batteryPercent%"
            canvas.drawText(battStr, leftCurX, headerY, metaPaint)
        }

        // Right Header: AM/PM aligns with the rightmost visible dot
        if (period != null) {
            val periodWidth = periodPaint.measureText(period)
            val periodX = (visualStartX + totalVisualWidth + dotRadius) - periodWidth
            canvas.drawText(period, periodX, headerY, periodPaint)
        }

        // ── Main Digits ─────────────────────────────────────────
        // Hour Digit 1
        drawDigit(canvas, d1, x1, startY, dotSize, gap)

        // Hour Digit 2
        drawDigit(canvas, d2, x2, startY, dotSize, gap)

        // 2-Dot Colon (Optical center guaranteed equal spacing to left and right digits)
        val colonDot1CenterY = startY + (2 * (dotSize + gap)) + dotRadius
        val colonDot2CenterY = startY + (4 * (dotSize + gap)) + dotRadius
        canvas.drawCircle(finalColonCenterX, colonDot1CenterY, dotRadius, colonPaint)
        canvas.drawCircle(finalColonCenterX, colonDot2CenterY, dotRadius, colonPaint)

        // Minute Digit 1
        drawDigit(canvas, d3, x3, startY, dotSize, gap)

        // Minute Digit 2
        drawDigit(canvas, d4, x4, startY, dotSize, gap)

        canvas.restore()
    }

    private fun getLeftmostCol(digit: Int): Int {
        val pattern = DotMatrixFont.getDigit(digit)
        for (col in 0 until 5) {
            for (row in 0 until 7) {
                if (pattern[row][col]) return col
            }
        }
        return 0
    }

    private fun getRightmostCol(digit: Int): Int {
        val pattern = DotMatrixFont.getDigit(digit)
        for (col in 4 downTo 0) {
            for (row in 0 until 7) {
                if (pattern[row][col]) return col
            }
        }
        return 4
    }

    private fun drawDigit(canvas: Canvas, digit: Int, x: Float, y: Float, dotSize: Float, gap: Float) {
        val pattern = DotMatrixFont.getDigit(digit)
        val r = dotSize / 2f
        for (row in 0 until 7) {
            for (col in 0 until 5) {
                if (pattern[row][col]) {
                    val cx = x + col * (dotSize + gap) + r
                    val cy = y + row * (dotSize + gap) + r
                    canvas.drawCircle(cx, cy, r, digitPaint)
                }
            }
        }
    }
}
