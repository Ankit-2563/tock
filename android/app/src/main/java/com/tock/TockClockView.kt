package com.tock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
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

    private val chunkyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val condensedPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── State ───────────────────────────────────────────────────
    private var theme: ColorTheme = ColorTheme.CLASSIC
    private var use24Hour = true
    private var burnInProtection = true
    private var lowPower = false

    var isPreview = false
    var mockTime: Pair<Int, Int>? = null

    private var batteryPercent = -1
    private var isCharging = false

    // Burn-in shift
    private var burnInOffsetX = 0f
    private var burnInOffsetY = 0f

    // Fonts
    private val nothingFont: Typeface = Typeface.createFromAsset(context.assets, "fonts/NothingFont5x7.ttf")
    private val poppinsFont: Typeface = Typeface.createFromAsset(context.assets, "fonts/Poppins-Regular.ttf")
    private val chunkyFont: Typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    private val condensedFont: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

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

        val activeFont = when (theme.style) {
            ThemeStyle.POPPINS -> poppinsFont
            ThemeStyle.CONDENSED_ORANGE -> condensedFont
            ThemeStyle.CHUNKY_BLUE, ThemeStyle.BUBBLE_PURPLE -> chunkyFont
            else -> nothingFont
        }
        metaPaint.typeface = activeFont
        periodPaint.typeface = activeFont

        poppinsPaint.typeface = poppinsFont
        poppinsPaint.color = theme.digitColor
        poppinsAmPmPaint.typeface = poppinsFont
        poppinsAmPmPaint.color = theme.periodColor

        chunkyPaint.typeface = chunkyFont
        condensedPaint.typeface = condensedFont
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isPreview) {
            handler.post(tickRunnable)
            try {
                context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (_: Exception) {}
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isPreview) {
            handler.removeCallbacks(tickRunnable)
            try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPreview) return false
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun updateBurnIn() {
        if (isPreview || !burnInProtection) {
            burnInOffsetX = 0f
            burnInOffsetY = 0f
            return
        }
        val density = resources.displayMetrics.density
        val step = 14f * density
        val minuteEpoch = (System.currentTimeMillis() / 60_000L).toInt()
        val phase = minuteEpoch % 12
        val angleRad = phase * (2.0 * PI / 12.0)
        burnInOffsetX = (cos(angleRad) * step).toFloat()
        burnInOffsetY = (sin(angleRad) * step).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        if (!isPreview) {
            canvas.translate(burnInOffsetX, burnInOffsetY)
        }

        val w = width.toFloat()
        val h = height.toFloat()

        val cal = Calendar.getInstance()
        val rawHour: Int
        val minute: Int

        if (isPreview) {
            val (mH, mM) = mockTime ?: Pair(20, 2)
            rawHour = mH
            minute = mM
        } else {
            rawHour = cal.get(Calendar.HOUR_OF_DAY)
            minute = cal.get(Calendar.MINUTE)
        }

        val hour: Int
        val period: String?
        if (use24Hour && !isPreview) {
            hour = rawHour
            period = null
        } else {
            val mod = rawHour % 12
            hour = if (mod == 0) 12 else mod
            period = if (rawHour >= 12) "PM" else "AM"
        }

        when (theme.style) {
            ThemeStyle.POPPINS -> drawPoppinsClock(canvas, w, h, hour, minute, period)
            ThemeStyle.CHUNKY_BLUE -> drawChunkyClock(canvas, w, h, hour, minute, isMultiColor = false)
            ThemeStyle.BUBBLE_PURPLE -> drawChunkyClock(canvas, w, h, hour, minute, isMultiColor = true)
            ThemeStyle.CONDENSED_ORANGE -> drawCondensedOrangeClock(canvas, w, h, hour, minute, period, cal)
            else -> drawDotMatrixClock(canvas, w, h, hour, minute, period, cal)
        }

        canvas.restore()
    }

    // ── Theme 2 & 3: Chunky Bold Retro & Bubble Purple ────────────
    private fun drawChunkyClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        hour: Int,
        minute: Int,
        isMultiColor: Boolean
    ) {
        val density = resources.displayMetrics.density
        val isLandscape = w > h
        val timeStr = String.format("%02d:%02d", hour, minute)

        val sidePadding = if (isLandscape) max(28f * density, w * 0.06f) else max(16f * density, w * 0.04f)
        val availableW = w - sidePadding * 2f
        val availableH = h * (if (isLandscape) 0.78f else 0.58f)

        val testSize = 200f * density
        chunkyPaint.textSize = testSize
        val rawWidth = chunkyPaint.measureText(timeStr)
        val scale = min(availableW / (rawWidth * 0.96f), availableH / (testSize * 0.75f))
        val mainFontSize = testSize * scale
        chunkyPaint.textSize = mainFontSize

        val fm = chunkyPaint.fontMetrics
        val textH = fm.descent - fm.ascent
        val centerY = (h / 2f) + (textH / 2f) - fm.descent

        val d1Str = String.format("%02d", hour).substring(0, 1)
        val d2Str = String.format("%02d", hour).substring(1, 2)
        val d3Str = String.format("%02d", minute).substring(0, 1)
        val d4Str = String.format("%02d", minute).substring(1, 2)

        val d1W = chunkyPaint.measureText(d1Str)
        val d2W = chunkyPaint.measureText(d2Str)
        val d3W = chunkyPaint.measureText(d3Str)
        val d4W = chunkyPaint.measureText(d4Str)

        val colonDotRadius = max(4f * density, mainFontSize * 0.055f)
        val colonSpacing = max(8f * density, mainFontSize * 0.12f)
        val colonW = (colonDotRadius * 2f) + (colonSpacing * 2f)

        val overlap = mainFontSize * 0.05f
        val totalBlockW = d1W + d2W - overlap + colonW + d3W + d4W - overlap
        var curX = (w - totalBlockW) / 2f

        // Colors for Chunky Blue
        val blueColors = intArrayOf(
            Color.parseColor("#0078FF"),
            Color.parseColor("#2990FF"),
            Color.parseColor("#0084FF"),
            Color.parseColor("#3896FF")
        )
        // Colors for Funky Bubble Purple
        val purpleColors = intArrayOf(
            Color.parseColor("#D8639F"),
            Color.parseColor("#9E93F8"),
            Color.parseColor("#8274F5"),
            Color.parseColor("#8E2192")
        )
        val colors = if (isMultiColor) purpleColors else blueColors

        // Draw d1
        chunkyPaint.color = colors[0]
        canvas.drawText(d1Str, curX, centerY, chunkyPaint)
        curX += d1W - overlap

        // Draw d2
        chunkyPaint.color = colors[1]
        canvas.drawText(d2Str, curX, centerY, chunkyPaint)
        curX += d2W

        // Draw Colon (Two clean white round dots)
        val colonCenterX = curX + colonSpacing + colonDotRadius
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val colonGap = mainFontSize * 0.16f
        canvas.drawCircle(colonCenterX, centerY - (textH * 0.35f) - colonGap, colonDotRadius, dotPaint)
        canvas.drawCircle(colonCenterX, centerY - (textH * 0.35f) + colonGap, colonDotRadius, dotPaint)
        curX += colonW

        // Draw d3
        chunkyPaint.color = colors[2]
        canvas.drawText(d3Str, curX, centerY, chunkyPaint)
        curX += d3W - overlap

        // Draw d4
        chunkyPaint.color = colors[3]
        canvas.drawText(d4Str, curX, centerY, chunkyPaint)
        curX += d4W

        // Top right "AM"
        val amPmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0F4F8")
            typeface = chunkyFont
            textSize = mainFontSize * 0.16f
        }
        val amX = curX + 6f * density
        val amY = centerY - (textH * 0.62f)
        canvas.drawText("AM", amX, amY, amPmPaint)
    }

    // ── Theme 5: Condensed Retro Orange (Light Mode) ──────────────
    private fun drawCondensedOrangeClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        hour: Int,
        minute: Int,
        period: String?,
        cal: Calendar
    ) {
        val density = resources.displayMetrics.density
        val orangeColor = Color.parseColor("#F2380C")
        val isLandscape = w > h

        // Date Header (Top Center)
        val days = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val months = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val dayStr = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val monthStr = months[cal.get(Calendar.MONTH)]
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val headerText = if (isPreview) "WED NOV 18" else "$dayStr $monthStr $dayOfMonth"

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = orangeColor
            typeface = condensedFont
            textSize = max(11f * density, min(w, h) * 0.07f)
            textAlign = Paint.Align.CENTER
        }
        val headerY = if (isLandscape) 22f * density else 28f * density
        canvas.drawText(headerText, w / 2f, headerY, headerPaint)

        // Main Digits: 20:02
        val timeStr = String.format("%02d:%02d", hour, minute)
        val testSize = 220f * density
        condensedPaint.textSize = testSize
        condensedPaint.color = orangeColor

        val availableW = w * 0.88f
        val availableH = h * 0.62f
        val rawWidth = condensedPaint.measureText(timeStr)
        val scale = min(availableW / rawWidth, availableH / (testSize * 0.8f))
        val mainFontSize = testSize * scale
        condensedPaint.textSize = mainFontSize

        val fm = condensedPaint.fontMetrics
        val textH = fm.descent - fm.ascent
        val centerY = (h / 2f) + (textH / 2f) - fm.descent

        val timeW = condensedPaint.measureText(timeStr)
        val startX = (w - timeW) / 2f
        canvas.drawText(timeStr, startX, centerY, condensedPaint)

        // Bottom "AM" (Centered)
        val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = orangeColor
            typeface = condensedFont
            textSize = max(12f * density, min(w, h) * 0.08f)
            textAlign = Paint.Align.CENTER
        }
        val bottomY = h - (if (isLandscape) 12f * density else 18f * density)
        canvas.drawText(period ?: "AM", w / 2f, bottomY, bottomPaint)
    }

    // ── Theme 4: Poppins Clean Typography Clock ──────────────────
    private fun drawPoppinsClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        hour: Int,
        minute: Int,
        period: String?
    ) {
        val density = resources.displayMetrics.density
        val isLandscape = w > h

        val timeStr = String.format("%02d:%02d", hour, minute)

        val sidePadding = if (isLandscape) max(28f * density, w * 0.06f) else max(16f * density, w * 0.04f)
        val availableW = w - sidePadding * 2f
        val availableH = h * (if (isLandscape) 0.74f else 0.52f)

        val testSize = 250f * density
        poppinsPaint.textSize = testSize
        poppinsAmPmPaint.textSize = testSize * 0.29f

        val rawTimeW = poppinsPaint.measureText(timeStr)
        val rawAmW = poppinsAmPmPaint.measureText(period ?: "AM") + 16f * density
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
        val amPmWidth = poppinsAmPmPaint.measureText(period ?: "AM")
        val totalBlockW = timeWidth + amPmGap + amPmWidth

        val startX = (w - totalBlockW) / 2f

        val fontMetrics = poppinsPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val baselineY = (h / 2f) + (textHeight / 2f) - fontMetrics.descent

        canvas.drawText(timeStr, startX, baselineY, poppinsPaint)
        val amPmX = startX + timeWidth + amPmGap
        canvas.drawText(period ?: "AM", amPmX, baselineY, poppinsAmPmPaint)
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

        val minSidePadding = if (isPreview) 16f * density else max(64f, 64f * density)
        val sidePadding = if (isLandscape) max(minSidePadding, w * 0.085f) else max(minSidePadding, w * 0.06f)

        val headerTopPadding = if (isLandscape) (if (isPreview) 10f * density else 24f * density) else 48f * density
        val metaFontSize = if (isPreview) max(8f * density, min(w, h) * 0.06f) else max(14f * density, min(w, h) * 0.045f)
        val periodFontSize = if (isPreview) max(9f * density, min(w, h) * 0.065f) else max(16f * density, min(w, h) * 0.05f)

        metaPaint.textSize = metaFontSize
        periodPaint.textSize = periodFontSize

        val headerY = headerTopPadding + metaFontSize
        val headerTotalHeight = headerY + (if (isPreview) 6f * density else 16f * density)
        val bottomMargin = if (isPreview) 8f * density else 20f * density

        val availableW = w - (sidePadding * 2f)
        val availableH = h - headerTotalHeight - bottomMargin

        val byWidth = availableW / 28.5f
        val byHeight = availableH / 8.5f
        val rawDot = if (isLandscape) min(byWidth, byHeight) else min(availableW / 28.5f, availableH / 12f)

        val dotSize = max(3.5f * density, rawDot)
        val dotRadius = dotSize / 2f
        val gap = max(1.5f, dotSize * 0.22f)
        val digitGap = max(4f * density, dotSize * 1.05f)

        val digitWidth = 5 * dotSize + 4 * gap
        val digitHeight = 7 * dotSize + 6 * gap

        val d1LeftCol = getLeftmostCol(d1)
        val d2RightCol = getRightmostCol(d2)
        val d3LeftCol = getLeftmostCol(d3)
        val d4RightCol = getRightmostCol(d4)

        val rawX1 = 0f
        val rawX2 = rawX1 + digitWidth + digitGap
        val d2RightDotX = rawX2 + d2RightCol * (dotSize + gap) + dotRadius

        val colonSpacing = max(6f * density, dotSize * 1.55f)

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
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val dateText = if (isPreview) "WED NOV 18" else "$dayStr $monthStr $dayOfMonth"

        val effectiveBattery = if (isPreview) 86 else batteryPercent
        val effectiveCharging = if (isPreview) true else isCharging

        var headerLeftX = d1LeftDotX + shiftX
        canvas.drawText(dateText, headerLeftX, headerY, metaPaint)
        headerLeftX += metaPaint.measureText(dateText) + (10f * density)

        if (effectiveCharging) {
            val boltH = metaFontSize * 0.95f
            val boltW = boltH * (ChargingBolt.SVG_WIDTH / ChargingBolt.SVG_HEIGHT)
            val boltTop = headerY - metaFontSize * 0.85f
            val scale = boltH / ChargingBolt.SVG_HEIGHT
            for (circle in ChargingBolt.circles) {
                val cx = headerLeftX + circle.cx * scale
                val cy = boltTop + circle.cy * scale
                val r = circle.r * scale
                canvas.drawCircle(cx, cy, r, boltPaint)
            }
            headerLeftX += boltW + (8f * density)
        }

        if (effectiveBattery >= 0) {
            val batText = "$effectiveBattery%"
            canvas.drawText(batText, headerLeftX, headerY, metaPaint)
        }

        val periodText = period ?: "AM"
        val periodW = periodPaint.measureText(periodText)
        val periodX = (d4RightDotX + shiftX) - periodW
        canvas.drawText(periodText, periodX, headerY, periodPaint)

        // Draw Digits
        drawDotDigit(canvas, d1, x1, startY, dotSize, gap)
        drawDotDigit(canvas, d2, x2, startY, dotSize, gap)

        // Draw Colon
        val colonR = dotRadius
        val dot1CenterY = startY + (2 * (dotSize + gap)) + colonR
        val dot2CenterY = startY + (4 * (dotSize + gap)) + colonR
        canvas.drawCircle(finalColonCenterX, dot1CenterY, colonR, colonPaint)
        canvas.drawCircle(finalColonCenterX, dot2CenterY, colonR, colonPaint)

        drawDotDigit(canvas, d3, x3, startY, dotSize, gap)
        drawDotDigit(canvas, d4, x4, startY, dotSize, gap)
    }

    private fun getLeftmostCol(digit: Int): Int {
        val matrix = DotMatrixFont.getDigit(digit)
        for (col in 0 until 5) {
            for (row in 0 until 7) {
                if (matrix[row][col]) return col
            }
        }
        return 0
    }

    private fun getRightmostCol(digit: Int): Int {
        val matrix = DotMatrixFont.getDigit(digit)
        for (col in 4 downTo 0) {
            for (row in 0 until 7) {
                if (matrix[row][col]) return col
            }
        }
        return 4
    }

    private fun drawDotDigit(
        canvas: Canvas,
        digit: Int,
        startX: Float,
        startY: Float,
        dotSize: Float,
        gap: Float
    ) {
        val matrix = DotMatrixFont.getDigit(digit)
        val dotRadius = dotSize / 2f
        for (row in 0 until 7) {
            val cy = startY + row * (dotSize + gap) + dotRadius
            for (col in 0 until 5) {
                if (matrix[row][col]) {
                    val cx = startX + col * (dotSize + gap) + dotRadius
                    canvas.drawCircle(cx, cy, dotRadius, digitPaint)
                }
            }
        }
    }
}
