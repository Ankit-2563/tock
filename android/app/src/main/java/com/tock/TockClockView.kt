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

    private val bloomDigitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bloomMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bloomColonPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val candyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val candyColonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val candyAmPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
    private val chairdrobeFont: Typeface = try {
        Typeface.createFromAsset(context.assets, "fonts/Chairdrobe Rounded Bold.otf")
    } catch (_: Exception) {
        Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val candyFont: Typeface = listOf(
        "fonts/ChironGoRoundTC-Black.otf",
        "fonts/ChironGoRoundTC-Black.ttf",
        "fonts/Chiron GoRound TC Black.otf",
        "fonts/Chiron GoRound TC Black.ttf",
        "fonts/ChironGoRoundTC.otf",
        "fonts/ChironGoRoundTC.ttf"
    ).firstNotNullOfOrNull { path ->
        try {
            Typeface.createFromAsset(context.assets, path)
        } catch (_: Exception) {
            null
        }
    } ?: Typeface.create("sans-serif-black", Typeface.BOLD)

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
            ThemeStyle.TOCK_BLOOM -> chairdrobeFont
            else -> nothingFont
        }
        metaPaint.typeface = activeFont
        periodPaint.typeface = activeFont

        poppinsPaint.typeface = poppinsFont
        poppinsPaint.color = theme.digitColor
        poppinsAmPmPaint.typeface = poppinsFont
        poppinsAmPmPaint.color = theme.periodColor

        bloomDigitPaint.typeface = chairdrobeFont
        bloomDigitPaint.color = theme.digitColor
        bloomMetaPaint.typeface = chairdrobeFont
        bloomMetaPaint.color = theme.metaColor
        bloomColonPaint.color = theme.colonColor
        bloomColonPaint.style = Paint.Style.FILL

        candyPaint.typeface = candyFont
        candyAmPaint.typeface = candyFont
        candyAmPaint.color = Color.parseColor("#E6E6E6")
        candyColonPaint.color = Color.parseColor("#E6E6E6")
        candyColonPaint.style = Paint.Style.FILL
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
            ThemeStyle.TOCK_BLOOM -> drawBloomClock(canvas, w, h, hour, minute, period, cal)
            ThemeStyle.CANDY_CLOCK -> drawCandyClock(canvas, w, h, hour, minute, period)
            else -> drawDotMatrixClock(canvas, w, h, hour, minute, period, cal)
        }

        canvas.restore()
    }

    // ── Theme 4: Candy Clock ─────────────────────────────────────
    private fun drawCandyClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        hour: Int,
        minute: Int,
        period: String?
    ) {
        val density = resources.displayMetrics.density
        val isLandscape = w > h

        val d1Str = String.format("%02d", hour).substring(0, 1)
        val d2Str = String.format("%02d", hour).substring(1, 2)
        val d3Str = String.format("%02d", minute).substring(0, 1)
        val d4Str = String.format("%02d", minute).substring(1, 2)

        val digitColors = intArrayOf(
            Color.parseColor("#D170B3"),
            Color.parseColor("#B09BF9"),
            Color.parseColor("#A590F7"),
            Color.parseColor("#9B3AA5")
        )

        val availableW = w * (if (isLandscape) 0.88f else 0.90f)
        val availableH = h * (if (isLandscape) 0.68f else 0.58f)

        val testSize = 220f * density
        candyPaint.textSize = testSize
        candyPaint.typeface = candyFont

        val w1 = candyPaint.measureText(d1Str)
        val w2 = candyPaint.measureText(d2Str)
        val w3 = candyPaint.measureText(d3Str)
        val w4 = candyPaint.measureText(d4Str)

        val fm = candyPaint.fontMetrics
        val digitH = fm.descent - fm.ascent

        val colonDotR = digitH * 0.082f
        val colonGap = digitH * 0.17f
        val colonTotalW = (colonGap * 2f) + (colonDotR * 2f)
        val digitGap = digitH * 0.09f

        val totalDigitsW = w1 + digitGap + w2 + colonTotalW + w3 + digitGap + w4

        val scale = min(availableW / totalDigitsW, availableH / digitH)
        val finalFontSize = testSize * scale
        candyPaint.textSize = finalFontSize

        val finalFm = candyPaint.fontMetrics
        val finalDigitH = finalFm.descent - finalFm.ascent
        val baselineY = (h / 2f) - (finalFm.ascent + finalFm.descent) / 2f

        val fw1 = candyPaint.measureText(d1Str)
        val fw2 = candyPaint.measureText(d2Str)
        val fw3 = candyPaint.measureText(d3Str)
        val fw4 = candyPaint.measureText(d4Str)

        val fColonDotR = finalDigitH * 0.082f
        val fColonGap = finalDigitH * 0.17f
        val fColonTotalW = (fColonGap * 2f) + (fColonDotR * 2f)
        val fDigitGap = finalDigitH * 0.09f

        val finalTotalW = fw1 + fDigitGap + fw2 + fColonTotalW + fw3 + fDigitGap + fw4
        var curX = (w - finalTotalW) / 2f

        // Draw d1
        candyPaint.color = digitColors[0]
        canvas.drawText(d1Str, curX, baselineY, candyPaint)
        curX += fw1 + fDigitGap

        // Draw d2
        candyPaint.color = digitColors[1]
        canvas.drawText(d2Str, curX, baselineY, candyPaint)
        curX += fw2

        // Draw Colon (Two #E6E6E6 dots)
        val colonCenterX = curX + fColonGap + fColonDotR
        val dotOffset = finalDigitH * 0.20f
        canvas.drawCircle(colonCenterX, (h / 2f) - dotOffset, fColonDotR, candyColonPaint)
        canvas.drawCircle(colonCenterX, (h / 2f) + dotOffset, fColonDotR, candyColonPaint)
        curX += fColonTotalW

        // Draw d3
        candyPaint.color = digitColors[2]
        canvas.drawText(d3Str, curX, baselineY, candyPaint)
        curX += fw3 + fDigitGap

        // Draw d4
        val d4StartX = curX
        candyPaint.color = digitColors[3]
        canvas.drawText(d4Str, d4StartX, baselineY, candyPaint)

        // AM at top right (above d4)
        if (isPreview || period != null) {
            val amText = if (isPreview) "AM" else period ?: "AM"
            candyAmPaint.textSize = finalFontSize * 0.17f
            val amX = d4StartX + fw4 + (2f * density)
            val amY = baselineY + finalFm.ascent + (finalDigitH * 0.18f)
            canvas.drawText(amText, min(amX, w - (12f * density) - candyAmPaint.measureText(amText)), max(amY, 24f * density), candyAmPaint)
        }
    }

    // ── Theme 3: Tock Bloom Clock ────────────────────────────────
    private fun drawBloomClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        hour: Int,
        minute: Int,
        period: String?,
        cal: Calendar
    ) {
        val density = resources.displayMetrics.density

        // 1. Top Date Header (e.g. "WED NOV 18")
        val headerText = if (isPreview) {
            "WED NOV 18"
        } else {
            val days = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
            val months = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
            val dayStr = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val monthStr = months[cal.get(Calendar.MONTH)]
            val dayNum = cal.get(Calendar.DAY_OF_MONTH)
            "$dayStr $monthStr $dayNum"
        }

        val headerSize = max(11f * density, min(w, h) * 0.095f)
        bloomMetaPaint.textSize = headerSize
        bloomMetaPaint.textAlign = Paint.Align.CENTER

        val hFm = bloomMetaPaint.fontMetrics
        val headerY = (h * 0.075f) - hFm.ascent
        canvas.drawText(headerText, w / 2f, headerY, bloomMetaPaint)

        // 2. Main Time ("20:02")
        val hStr = String.format("%02d", hour)
        val mStr = String.format("%02d", minute)

        val availableW = w * 0.82f
        val availableH = h * 0.54f

        val testSize = 200f * density
        bloomDigitPaint.textSize = testSize
        val rawHw = bloomDigitPaint.measureText(hStr)
        val rawMw = bloomDigitPaint.measureText(mStr)
        val rawFm = bloomDigitPaint.fontMetrics
        val rawDigitH = rawFm.descent - rawFm.ascent

        val rawColonDotR = rawDigitH * 0.084f
        val rawColonGap = rawDigitH * 0.22f
        val rawColonTotalW = (rawColonGap * 2f) + (rawColonDotR * 2f)
        val rawTotalW = rawHw + rawColonTotalW + rawMw

        val scale = min(availableW / rawTotalW, availableH / rawDigitH)
        val finalFontSize = testSize * scale
        bloomDigitPaint.textSize = finalFontSize

        val finalFm = bloomDigitPaint.fontMetrics
        val finalDigitH = finalFm.descent - finalFm.ascent
        val baselineY = (h / 2f) - (finalFm.ascent + finalFm.descent) / 2f

        val finalHw = bloomDigitPaint.measureText(hStr)
        val finalMw = bloomDigitPaint.measureText(mStr)
        val colonDotR = finalDigitH * 0.084f
        val colonGap = finalDigitH * 0.22f
        val colonTotalW = (colonGap * 2f) + (colonDotR * 2f)
        val totalW = finalHw + colonTotalW + finalMw
        val startX = (w - totalW) / 2f

        // Draw Hour Digits
        canvas.drawText(hStr, startX, baselineY, bloomDigitPaint)

        // Draw Two Colon Dots (centered vertically with digits)
        val colonCenterX = startX + finalHw + colonGap + colonDotR
        val dotOffset = finalDigitH * 0.205f
        canvas.drawCircle(colonCenterX, (h / 2f) - dotOffset, colonDotR, bloomColonPaint)
        canvas.drawCircle(colonCenterX, (h / 2f) + dotOffset, colonDotR, bloomColonPaint)

        // Draw Minute Digits
        val minuteStartX = startX + finalHw + colonTotalW
        canvas.drawText(mStr, minuteStartX, baselineY, bloomDigitPaint)

        // 3. Bottom Period ("AM" / "PM")
        if (isPreview || period != null) {
            val amText = if (isPreview) "AM" else period ?: "AM"
            bloomMetaPaint.textSize = headerSize
            val amY = h - (h * 0.07f)
            canvas.drawText(amText, w / 2f, amY, bloomMetaPaint)
        }
    }

    // ── Theme 2: Poppins Clean Typography Clock ──────────────────
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
