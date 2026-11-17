package com.tock

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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class TockClockView @JvmOverloads constructor(
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

    private val poppinsPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val poppinsAmPmPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

    // Fonts
    private val nothingFont: Typeface = Typeface.createFromAsset(context.assets, "fonts/NothingFont5x7.ttf")
    private val poppinsFont: Typeface = Typeface.createFromAsset(context.assets, "fonts/Poppins-Regular.ttf")

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

        val activeFont = if (theme.style == ThemeStyle.POPPINS) poppinsFont else nothingFont
        metaPaint.typeface = activeFont
        periodPaint.typeface = activeFont

        poppinsPaint.typeface = poppinsFont
        poppinsPaint.color = theme.digitColor
        poppinsAmPmPaint.typeface = poppinsFont
        poppinsAmPmPaint.color = theme.periodColor
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
        val density = resources.displayMetrics.density
        // 14dp orbital shift (~38-42px on FHD+). Exceeds dot diameter so lit OLED subpixels completely clear their previous footprint.
        val step = 14f * density
        // Advances by 1 orbital step every minute, synchronized with minute rollover
        val minuteEpoch = (System.currentTimeMillis() / 60_000L).toInt()
        val phase = minuteEpoch % 12
        val angleRad = phase * (2.0 * PI / 12.0)
        burnInOffsetX = (cos(angleRad) * step).toFloat()
        burnInOffsetY = (sin(angleRad) * step).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(burnInOffsetX, burnInOffsetY)

        val w = width.toFloat()
        val h = height.toFloat()

        val cal = Calendar.getInstance()
        val rawHour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

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

        if (theme.style == ThemeStyle.POPPINS) {
            drawPoppinsClock(canvas, w, h, hour, minute, period, cal)
        } else {
            drawDotMatrixClock(canvas, w, h, hour, minute, period, cal)
        }

        canvas.restore()
    }

    // ── Theme 2: Poppins Clean Typography Clock ──────────────────
    private fun drawPoppinsClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        hour: Int,
        minute: Int,
        period: String?,
        cal: Calendar
    ) {
        val density = resources.displayMetrics.density
        val isLandscape = w > h

        val timeStr = String.format("%02d:%02d", hour, minute)

        val sidePadding = if (isLandscape) max(64f * density, w * 0.08f) else max(32f * density, w * 0.05f)
        val availableW = w - sidePadding * 2f
        val availableH = h * (if (isLandscape) 0.74f else 0.52f)

        // Measure with reference size
        val testSize = 250f * density
        poppinsPaint.textSize = testSize
        poppinsAmPmPaint.textSize = testSize * 0.29f

        val rawTimeW = poppinsPaint.measureText(timeStr)
        val rawAmW = if (period != null) poppinsAmPmPaint.measureText(period) + 16f * density else 0f
        val rawTotalW = rawTimeW + rawAmW

        val scaleW = availableW / rawTotalW
        val scaleH = availableH / (testSize * 0.72f)
        val scale = min(scaleW, scaleH)

        val mainFontSize = testSize * scale
        val amPmFontSize = mainFontSize * 0.29f
        poppinsPaint.textSize = mainFontSize
        poppinsAmPmPaint.textSize = amPmFontSize

        val timeWidth = poppinsPaint.measureText(timeStr)
        val amPmGap = 16f * density * scale
        val amPmWidth = if (period != null) poppinsAmPmPaint.measureText(period) else 0f
        val totalBlockW = timeWidth + (if (period != null) amPmGap + amPmWidth else 0f)

        val startX = (w - totalBlockW) / 2f

        val fontMetrics = poppinsPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val baselineY = (h / 2f) + (textHeight / 2f) - fontMetrics.descent

        // Draw digits in Poppins font
        canvas.drawText(timeStr, startX, baselineY, poppinsPaint)

        // Draw "AM" or "PM" sitting at the baseline of the digits
        if (period != null) {
            val amPmX = startX + timeWidth + amPmGap
            canvas.drawText(period, amPmX, baselineY, poppinsAmPmPaint)
        }
    }

    // ── Theme 1: Nothing 5x7 Dot Matrix Clock ────────────────────
    private fun drawDotMatrixClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        hour: Int,
        minute: Int,
        period: String?,
        cal: Calendar
    ) {
        val isLandscape = w > h
        val density = resources.displayMetrics.density

        val d1 = hour / 10
        val d2 = hour % 10
        val d3 = minute / 10
        val d4 = minute % 10

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
        val digitGap = max(8f * density, dotSize * 1.05f)

        val digitWidth = 5 * dotSize + 4 * gap
        val digitHeight = 7 * dotSize + 6 * gap

        // Optical column bounds
        val d1LeftCol = getLeftmostCol(d1)
        val d2RightCol = getRightmostCol(d2)
        val d3LeftCol = getLeftmostCol(d3)
        val d4RightCol = getRightmostCol(d4)

        val rawX1 = 0f
        val rawX2 = rawX1 + digitWidth + digitGap
        val d2RightDotX = rawX2 + d2RightCol * (dotSize + gap) + dotRadius

        val colonSpacing = max(10f * density, dotSize * 1.55f)

        val colonCenterX = d2RightDotX + colonSpacing
        val d3LeftDotX = colonCenterX + colonSpacing
        val rawX3 = d3LeftDotX - (d3LeftCol * (dotSize + gap) + dotRadius)
        val rawX4 = rawX3 + digitWidth + digitGap

        val d1LeftDotX = rawX1 + d1LeftCol * (dotSize + gap) + dotRadius
        val d4RightDotX = rawX4 + d4RightCol * (dotSize + gap) + dotRadius

        val totalVisualWidth = d4RightDotX - d1LeftDotX
        val visualStartX = (w - totalVisualWidth) / 2f
        val shiftX = visualStartX - d1LeftDotX

        val x1 = rawX1 + shiftX
        val x2 = rawX2 + shiftX
        val finalColonCenterX = colonCenterX + shiftX
        val x3 = rawX3 + shiftX
        val x4 = rawX4 + shiftX

        val startY = headerTotalHeight + (h - headerTotalHeight - digitHeight) / 2f

        // Top Header
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

        if (period != null) {
            val periodWidth = periodPaint.measureText(period)
            val periodX = (visualStartX + totalVisualWidth + dotRadius) - periodWidth
            canvas.drawText(period, periodX, headerY, periodPaint)
        }

        // Main Digits
        drawDigit(canvas, d1, x1, startY, dotSize, gap)
        drawDigit(canvas, d2, x2, startY, dotSize, gap)

        val colonDot1CenterY = startY + (2 * (dotSize + gap)) + dotRadius
        val colonDot2CenterY = startY + (4 * (dotSize + gap)) + dotRadius
        canvas.drawCircle(finalColonCenterX, colonDot1CenterY, dotRadius, colonPaint)
        canvas.drawCircle(finalColonCenterX, colonDot2CenterY, dotRadius, colonPaint)

        drawDigit(canvas, d3, x3, startY, dotSize, gap)
        drawDigit(canvas, d4, x4, startY, dotSize, gap)
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
