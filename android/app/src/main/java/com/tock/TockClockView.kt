package com.tock

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
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

    // ── Digit Transition State ──────────────────────────────────
    private class DigitTransition {
        var prevDigit: Int = -1
        var currentDigit: Int = -1
        var targetDigit: Int = -1
        var progress: Float = 1f
    }

    private val digitTransitions = Array(4) { DigitTransition() }
    private var digitAnimator: ValueAnimator? = null

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

    private val bigTymPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bigTymPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bigTymColonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bigTymAmPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

    // ── Acoustic Sound Feedback ──────────────────────────────────
    private var soundPool: SoundPool? = null
    private var tickSoundId = 0
    private var hourSoundId = 0
    private var soundsLoaded = false

    private fun initAudio() {
        if (isPreview || soundPool != null) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attrs)
                .build().apply {
                    setOnLoadCompleteListener { _, _, status ->
                        if (status == 0) soundsLoaded = true
                    }
                    tickSoundId = load(context, R.raw.tick_minute, 1)
                    hourSoundId = load(context, R.raw.chime_hour, 1)
                }
        } catch (_: Exception) {}
    }

    private fun playMinuteSound() {
        if (!soundsLoaded || !TockPreferences.isSoundEnabled(context)) return
        try {
            soundPool?.play(tickSoundId, 0.70f, 0.70f, 1, 0, 1.0f)
        } catch (_: Exception) {}
    }

    private fun playHourSound() {
        if (!soundsLoaded || !TockPreferences.isSoundEnabled(context)) return
        try {
            soundPool?.play(hourSoundId, 0.90f, 0.90f, 1, 0, 1.0f)
        } catch (_: Exception) {}
    }

    // Fonts
    private val nothingFont: Typeface = Typeface.createFromAsset(context.assets, "fonts/NothingFont5x7.ttf")
    private val poppinsFont: Typeface = Typeface.createFromAsset(context.assets, "fonts/Poppins-Regular.ttf")
    private val chairdrobeFont: Typeface = try {
        Typeface.createFromAsset(context.assets, "fonts/Chairdrobe Rounded Bold.otf")
    } catch (_: Exception) {
        Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val fredokaFont: Typeface = try {
        Typeface.createFromAsset(context.assets, "fonts/Fredoka-Bold.ttf")
    } catch (_: Exception) {
        Typeface.create("sans-serif-black", Typeface.BOLD)
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

        bigTymPaint1.typeface = fredokaFont
        bigTymPaint1.color = Color.parseColor("#0083EA")
        bigTymPaint1.alpha = 255

        bigTymPaint2.typeface = fredokaFont
        bigTymPaint2.color = Color.parseColor("#4CB0FD")
        bigTymPaint2.alpha = 204 // 80% opacity for 2nd digit of hrs and mins

        bigTymColonPaint.color = Color.parseColor("#E6E6E6")
        bigTymColonPaint.style = Paint.Style.FILL

        bigTymAmPaint.typeface = fredokaFont
        bigTymAmPaint.color = Color.parseColor("#FFFFFF")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isPreview) {
            initAudio()
            handler.post(tickRunnable)
            try {
                context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (_: Exception) {}
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        digitAnimator?.cancel()
        if (!isPreview) {
            handler.removeCallbacks(tickRunnable)
            try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
            soundPool?.release()
            soundPool = null
            soundsLoaded = false
        }
    }

    private fun updateDigits(d0: Int, d1: Int, d2: Int, d3: Int) {
        val newDigits = intArrayOf(d0, d1, d2, d3)
        if (digitTransitions[0].currentDigit == -1 || isPreview) {
            for (i in 0 until 4) {
                digitTransitions[i].prevDigit = newDigits[i]
                digitTransitions[i].currentDigit = newDigits[i]
                digitTransitions[i].targetDigit = newDigits[i]
                digitTransitions[i].progress = 1f
            }
            return
        }

        val hourChanged = (digitTransitions[0].targetDigit != newDigits[0]) || (digitTransitions[1].targetDigit != newDigits[1])
        var anyChanged = false
        for (i in 0 until 4) {
            if (digitTransitions[i].targetDigit != newDigits[i]) {
                digitTransitions[i].prevDigit = digitTransitions[i].targetDigit
                digitTransitions[i].targetDigit = newDigits[i]
                digitTransitions[i].progress = 0f
                anyChanged = true
            }
        }

        if (anyChanged) {
            if (hourChanged) {
                playHourSound()
            } else {
                playMinuteSound()
            }
            digitAnimator?.cancel()
            digitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 850L // slow, smooth, aesthetically pleasing
                interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f) // silky deceleration curve
                addUpdateListener { anim ->
                    val p = anim.animatedValue as Float
                    for (i in 0 until 4) {
                        if (digitTransitions[i].progress < 1f) {
                            digitTransitions[i].progress = p
                            if (p >= 1f) {
                                digitTransitions[i].currentDigit = digitTransitions[i].targetDigit
                            }
                        }
                    }
                    invalidate()
                }
                start()
            }
        }
    }

    private fun drawAnimatedDotDigit(
        canvas: Canvas,
        digitIndex: Int,
        targetDigit: Int,
        startX: Float,
        startY: Float,
        dotSize: Float,
        gap: Float,
        digitWidth: Float,
        digitHeight: Float
    ) {
        val trans = digitTransitions[digitIndex]
        if (trans.progress >= 1f || trans.prevDigit == targetDigit) {
            drawDotDigit(canvas, targetDigit, startX, startY, dotSize, gap)
            return
        }

        val p = trans.progress
        val slideDist = digitHeight * 0.40f
        val origAlpha = digitPaint.alpha

        canvas.save()
        val clipPad = dotSize * 0.5f
        canvas.clipRect(
            startX - clipPad,
            startY - clipPad,
            startX + digitWidth + clipPad,
            startY + digitHeight + clipPad
        )

        // Old digit sliding up and fading out
        val oldAlpha = (origAlpha * (1f - p)).toInt().coerceIn(0, 255)
        if (oldAlpha > 0) {
            digitPaint.alpha = oldAlpha
            drawDotDigit(canvas, trans.prevDigit, startX, startY - (p * slideDist), dotSize, gap)
        }

        // New digit sliding in from below and fading in
        val newAlpha = (origAlpha * p).toInt().coerceIn(0, 255)
        if (newAlpha > 0) {
            digitPaint.alpha = newAlpha
            drawDotDigit(canvas, targetDigit, startX, startY + ((1f - p) * slideDist), dotSize, gap)
        }

        digitPaint.alpha = origAlpha
        canvas.restore()
    }

    private fun drawAnimatedTextDigit(
        canvas: Canvas,
        digitIndex: Int,
        targetDigit: Int,
        slotX: Float,
        baselineY: Float,
        paint: Paint,
        slotWidth: Float,
        digitH: Float
    ) {
        val trans = digitTransitions[digitIndex]
        val targetStr = targetDigit.toString()
        val targetCharW = paint.measureText(targetStr)
        val targetX = slotX + (slotWidth - targetCharW) / 2f

        if (trans.progress >= 1f || trans.prevDigit == targetDigit) {
            canvas.drawText(targetStr, targetX, baselineY, paint)
            return
        }

        val p = trans.progress
        val slideDist = digitH * 0.40f
        val origAlpha = paint.alpha

        canvas.save()
        val clipTop = baselineY - digitH * 1.15f
        val clipBottom = baselineY + digitH * 0.45f
        val clipPad = slotWidth * 0.15f
        canvas.clipRect(slotX - clipPad, clipTop, slotX + slotWidth + clipPad, clipBottom)

        // 1. Old digit sliding up and fading out
        val oldAlpha = (origAlpha * (1f - p)).toInt().coerceIn(0, 255)
        if (oldAlpha > 0) {
            val prevStr = trans.prevDigit.toString()
            val prevCharW = paint.measureText(prevStr)
            val prevX = slotX + (slotWidth - prevCharW) / 2f
            paint.alpha = oldAlpha
            canvas.drawText(prevStr, prevX, baselineY - (p * slideDist), paint)
        }

        // 2. New digit sliding in from below and fading in
        val newAlpha = (origAlpha * p).toInt().coerceIn(0, 255)
        if (newAlpha > 0) {
            paint.alpha = newAlpha
            canvas.drawText(targetStr, targetX, baselineY + ((1f - p) * slideDist), paint)
        }

        paint.alpha = origAlpha
        canvas.restore()
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

        val d0 = hour / 10
        val d1 = hour % 10
        val d2 = minute / 10
        val d3 = minute % 10

        updateDigits(d0, d1, d2, d3)

        when (theme.style) {
            ThemeStyle.POPPINS -> drawPoppinsClock(canvas, w, h, d0, d1, d2, d3, period)
            ThemeStyle.TOCK_BLOOM -> drawBloomClock(canvas, w, h, d0, d1, d2, d3, period, cal)
            ThemeStyle.CANDY_CLOCK -> drawCandyClock(canvas, w, h, d0, d1, d2, d3, period)
            ThemeStyle.BIG_TYM_ENERGY -> drawBigTymEnergyClock(canvas, w, h, d0, d1, d2, d3, period)
            else -> drawDotMatrixClock(canvas, w, h, d0, d1, d2, d3, period, cal)
        }

        canvas.restore()
    }

    // ── Theme 5: Big Tym Energy Clock ─────────────────────────────
    private fun drawBigTymEnergyClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        d0: Int,
        d1: Int,
        d2: Int,
        d3: Int,
        period: String?
    ) {
        val density = resources.displayMetrics.density
        val isLandscape = w > h

        val availableW = w * (if (isLandscape) 0.96f else 0.96f)
        val availableH = h * (if (isLandscape) 0.82f else 0.76f)

        val testSize = 250f * density
        bigTymPaint1.textSize = testSize
        bigTymPaint2.textSize = testSize

        // Fixed slot width using reference digit "0" to prevent layout shifts
        val testRefW = bigTymPaint1.measureText("0")
        val rawDigitH = testSize * 0.70f

        // Overlap: d0-d1 overlap and d2-d3 overlap by 20% of slot width
        val rawOverlap = testRefW * 0.20f
        val rawColonDotR = rawDigitH * 0.085f
        val rawColonGap = rawColonDotR * 2.0f

        val rawTotalW = (testRefW * 2f - rawOverlap) * 2f + rawColonGap

        val scale = min(availableW / rawTotalW, availableH / rawDigitH)
        val finalFontSize = testSize * scale
        bigTymPaint1.textSize = finalFontSize
        bigTymPaint2.textSize = finalFontSize

        val finalDigitH = finalFontSize * 0.70f
        val baselineY = (h / 2f) + (finalDigitH / 2f)

        val slotW = bigTymPaint1.measureText("0")
        val fOverlap = slotW * 0.20f
        val fColonDotR = finalDigitH * 0.085f
        val fColonGap = fColonDotR * 2.0f

        val totalW = (slotW * 2f - fOverlap) * 2f + fColonGap
        val startX = (w - totalW) / 2f

        val slot0X = startX
        val slot1X = slot0X + slotW - fOverlap
        val slot1Right = slot1X + slotW

        val slot2X = slot1Right + fColonGap
        val slot3X = slot2X + slotW - fOverlap

        val colonCenterX = slot1Right + (fColonGap / 2f)

        // 1. Draw d0 (100% opacity #0083EA)
        drawAnimatedTextDigit(canvas, 0, d0, slot0X, baselineY, bigTymPaint1, slotW, finalDigitH)

        // 2. Draw d1 (80% opacity #4CB0FD, overlaps d0)
        drawAnimatedTextDigit(canvas, 1, d1, slot1X, baselineY, bigTymPaint2, slotW, finalDigitH)

        // 3. Draw d2 (100% opacity #0083EA)
        drawAnimatedTextDigit(canvas, 2, d2, slot2X, baselineY, bigTymPaint1, slotW, finalDigitH)

        // 4. Draw d3 (80% opacity #4CB0FD, overlaps d2)
        drawAnimatedTextDigit(canvas, 3, d3, slot3X, baselineY, bigTymPaint2, slotW, finalDigitH)

        // 5. Draw Colon (Two #E6E6E6 dots at the seam between d1 and d2)
        val dotOffset = finalDigitH * 0.20f
        canvas.drawCircle(colonCenterX, (h / 2f) - dotOffset, fColonDotR, bigTymColonPaint)
        canvas.drawCircle(colonCenterX, (h / 2f) + dotOffset, fColonDotR, bigTymColonPaint)

        // 6. AM/PM at top right — smaller
        if (isPreview || period != null) {
            val amText = if (isPreview) "AM" else period ?: "AM"
            bigTymAmPaint.textSize = finalFontSize * 0.12f
            val amW = bigTymAmPaint.measureText(amText)
            val amX = slot3X + slotW - amW
            val amY = (h / 2f) - (finalDigitH / 2f) - (4f * density)
            canvas.drawText(amText, min(amX, w - (12f * density) - amW), max(amY, 20f * density), bigTymAmPaint)
        }
    }

    // ── Theme 4: Candy Clock ─────────────────────────────────────
    private fun drawCandyClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        d0: Int,
        d1: Int,
        d2: Int,
        d3: Int,
        period: String?
    ) {
        val density = resources.displayMetrics.density
        val isLandscape = w > h

        val digitColors = intArrayOf(
            Color.parseColor("#D170B3"),
            Color.parseColor("#B09BF9"),
            Color.parseColor("#A590F7"),
            Color.parseColor("#9B3AA5")
        )

        // Elongate font height: condense horizontal width by 20% and expand available height
        candyPaint.textScaleX = 0.80f

        val availableW = w * (if (isLandscape) 0.92f else 0.90f)
        val availableH = h * (if (isLandscape) 0.80f else 0.70f)

        val testSize = 250f * density
        candyPaint.textSize = testSize
        candyPaint.typeface = candyFont

        // Fixed slot width using reference digit "0" to prevent layout shifts
        val testRefW = candyPaint.measureText("0")
        val fm = candyPaint.fontMetrics
        val digitH = fm.descent - fm.ascent

        val colonDotR = digitH * 0.082f
        val colonGap = digitH * 0.10f
        val colonTotalW = (colonGap * 2f) + (colonDotR * 2f)
        val digitGap = digitH * 0.06f

        val totalDigitsW = (testRefW * 4f) + (digitGap * 2f) + colonTotalW

        val scale = min(availableW / totalDigitsW, availableH / digitH)
        val finalFontSize = testSize * scale
        candyPaint.textSize = finalFontSize

        val finalFm = candyPaint.fontMetrics
        val finalDigitH = finalFm.descent - finalFm.ascent
        val baselineY = (h / 2f) - (finalFm.ascent + finalFm.descent) / 2f

        val slotW = candyPaint.measureText("0")
        val fColonDotR = finalDigitH * 0.082f
        val fColonGap = finalDigitH * 0.10f
        val fColonTotalW = (fColonGap * 2f) + (fColonDotR * 2f)
        val fDigitGap = finalDigitH * 0.06f

        // Total width of digits + colon only (no AM/PM included in centering)
        val digitsOnlyW = (slotW * 4f) + (fDigitGap * 2f) + fColonTotalW
        val digitsStartX = (w - digitsOnlyW) / 2f

        val slot0X = digitsStartX
        val slot1X = slot0X + slotW + fDigitGap
        val colonCenterX = slot1X + slotW + fColonGap + fColonDotR
        val slot2X = colonCenterX + fColonDotR + fColonGap
        val slot3X = slot2X + slotW + fDigitGap

        // Draw d0
        candyPaint.color = digitColors[0]
        drawAnimatedTextDigit(canvas, 0, d0, slot0X, baselineY, candyPaint, slotW, finalDigitH)

        // Draw d1
        candyPaint.color = digitColors[1]
        drawAnimatedTextDigit(canvas, 1, d1, slot1X, baselineY, candyPaint, slotW, finalDigitH)

        // Draw Colon (Two #E6E6E6 dots)
        val dotOffset = finalDigitH * 0.19f
        canvas.drawCircle(colonCenterX, (h / 2f) - dotOffset, fColonDotR, candyColonPaint)
        canvas.drawCircle(colonCenterX, (h / 2f) + dotOffset, fColonDotR, candyColonPaint)

        // Draw d2
        candyPaint.color = digitColors[2]
        drawAnimatedTextDigit(canvas, 2, d2, slot2X, baselineY, candyPaint, slotW, finalDigitH)

        // Draw d3
        candyPaint.color = digitColors[3]
        drawAnimatedTextDigit(canvas, 3, d3, slot3X, baselineY, candyPaint, slotW, finalDigitH)

        // AM at top right (above d3)
        if (isPreview || period != null) {
            val amText = if (isPreview) "AM" else period ?: "AM"
            candyAmPaint.textScaleX = 1.0f
            candyAmPaint.textSize = finalFontSize * 0.14f
            val amW = candyAmPaint.measureText(amText)
            val amX = w - (16f * density) - amW
            val amY = baselineY + finalFm.ascent + (finalDigitH * 0.16f)
            canvas.drawText(amText, amX, max(amY, 22f * density), candyAmPaint)
        }
    }

    // ── Theme 3: Tock Bloom Clock ────────────────────────────────
    private fun drawBloomClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        d0: Int,
        d1: Int,
        d2: Int,
        d3: Int,
        period: String?,
        cal: Calendar
    ) {
        val density = resources.displayMetrics.density
        val isLandscape = w > h

        // 1. Top Date Header (e.g. "WED NOV 18") — larger
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

        val headerSize = if (isLandscape) max(16f * density, h * 0.09f) else max(13f * density, min(w, h) * 0.11f)
        bloomMetaPaint.textSize = headerSize
        bloomMetaPaint.textAlign = Paint.Align.CENTER

        val headerY = if (isLandscape) max(18f * density, h * 0.10f) else (h * 0.085f) - bloomMetaPaint.fontMetrics.ascent
        canvas.drawText(headerText, w / 2f, headerY, bloomMetaPaint)

        // 2. Main Time ("20:02") — slightly smaller to leave room for header/footer
        val availableW = w * (if (isLandscape) 0.90f else 0.84f)
        val availableH = h * (if (isLandscape) 0.65f else 0.48f)

        val testSize = 250f * density
        bloomDigitPaint.textSize = testSize
        // Fixed slot width using reference digit "0" to prevent layout shifting
        val testRefW = bloomDigitPaint.measureText("0")
        val rawDigitH = testSize * 0.71f

        val rawColonDotR = rawDigitH * 0.082f
        val rawColonGap = rawDigitH * 0.14f
        val rawColonTotalW = (rawColonGap * 2f) + (rawColonDotR * 2f)
        val rawDigitGap = rawDigitH * 0.05f
        val rawTotalW = (testRefW * 4f) + (rawDigitGap * 2f) + rawColonTotalW

        val scale = min(availableW / rawTotalW, availableH / rawDigitH)
        val finalFontSize = testSize * scale
        bloomDigitPaint.textSize = finalFontSize

        val finalDigitH = finalFontSize * 0.71f
        val baselineY = (h / 2f) + (finalDigitH / 2f)

        val slotW = bloomDigitPaint.measureText("0")
        val colonDotR = finalDigitH * 0.082f
        val colonGap = finalDigitH * 0.14f
        val colonTotalW = (colonGap * 2f) + (colonDotR * 2f)
        val fDigitGap = finalDigitH * 0.05f
        val totalW = (slotW * 4f) + (fDigitGap * 2f) + colonTotalW
        val startX = (w - totalW) / 2f

        val slot0X = startX
        val slot1X = slot0X + slotW + fDigitGap
        val colonCenterX = slot1X + slotW + colonGap + colonDotR
        val slot2X = colonCenterX + colonDotR + colonGap
        val slot3X = slot2X + slotW + fDigitGap

        drawAnimatedTextDigit(canvas, 0, d0, slot0X, baselineY, bloomDigitPaint, slotW, finalDigitH)
        drawAnimatedTextDigit(canvas, 1, d1, slot1X, baselineY, bloomDigitPaint, slotW, finalDigitH)

        // Draw Two Colon Dots (centered vertically with digits)
        val dotOffset = finalDigitH * 0.20f
        canvas.drawCircle(colonCenterX, (h / 2f) - dotOffset, colonDotR, bloomColonPaint)
        canvas.drawCircle(colonCenterX, (h / 2f) + dotOffset, colonDotR, bloomColonPaint)

        drawAnimatedTextDigit(canvas, 2, d2, slot2X, baselineY, bloomDigitPaint, slotW, finalDigitH)
        drawAnimatedTextDigit(canvas, 3, d3, slot3X, baselineY, bloomDigitPaint, slotW, finalDigitH)

        // 3. Bottom Period ("AM" / "PM") — larger
        if (isPreview || period != null) {
            val amText = if (isPreview) "AM" else period ?: "AM"
            bloomMetaPaint.textSize = headerSize
            val amY = if (isLandscape) h - max(14f * density, h * 0.055f) else h - (h * 0.06f)
            canvas.drawText(amText, w / 2f, amY, bloomMetaPaint)
        }
    }

    // ── Theme 2: Poppins Clean Typography Clock ──────────────────
    private fun drawPoppinsClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        d0: Int,
        d1: Int,
        d2: Int,
        d3: Int,
        period: String?
    ) {
        val density = resources.displayMetrics.density
        val isLandscape = w > h

        val sidePadding = if (isLandscape) max(28f * density, w * 0.06f) else max(16f * density, w * 0.04f)
        val availableW = w - sidePadding * 2f
        val availableH = h * (if (isLandscape) 0.74f else 0.52f)

        val testSize = 250f * density
        poppinsPaint.textSize = testSize
        poppinsAmPmPaint.textSize = testSize * 0.29f

        // Use fixed reference digit width to prevent layout shifts when digits change
        val testRefW = poppinsPaint.measureText("0")
        val testColonW = poppinsPaint.measureText(":")
        val testAmW = poppinsAmPmPaint.measureText("PM") + (16f * density)
        val testDigitGap = testSize * 0.02f
        val testColonGap = testSize * 0.03f
        val rawTotalW = (testRefW * 4f) + (testDigitGap * 2f) + (testColonGap * 2f) + testColonW + testAmW

        val scaleW = availableW / rawTotalW
        val scaleH = availableH / (testSize * 0.72f)
        val scale = min(scaleW, scaleH)

        val mainFontSize = testSize * scale
        val amPmFontSize = mainFontSize * 0.29f
        poppinsPaint.textSize = mainFontSize
        poppinsAmPmPaint.textSize = amPmFontSize

        val fontMetrics = poppinsPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val baselineY = (h / 2f) + (textHeight / 2f) - fontMetrics.descent

        val slotW = poppinsPaint.measureText("0")
        val wColon = poppinsPaint.measureText(":")
        val digitGap = mainFontSize * 0.02f
        val colonGap = mainFontSize * 0.03f
        val amPmGap = 16f * density * scale
        val amPmText = period ?: "AM"
        val amPmWidth = poppinsAmPmPaint.measureText(amPmText)

        val totalBlockW = (slotW * 4f) + (digitGap * 2f) + (colonGap * 2f) + wColon + amPmGap + amPmWidth
        val startX = (w - totalBlockW) / 2f

        val slot0X = startX
        val slot1X = slot0X + slotW + digitGap
        val colonX = slot1X + slotW + colonGap
        val slot2X = colonX + wColon + colonGap
        val slot3X = slot2X + slotW + digitGap
        val amPmX = slot3X + slotW + amPmGap

        drawAnimatedTextDigit(canvas, 0, d0, slot0X, baselineY, poppinsPaint, slotW, textHeight)
        drawAnimatedTextDigit(canvas, 1, d1, slot1X, baselineY, poppinsPaint, slotW, textHeight)
        canvas.drawText(":", colonX, baselineY, poppinsPaint)
        drawAnimatedTextDigit(canvas, 2, d2, slot2X, baselineY, poppinsPaint, slotW, textHeight)
        drawAnimatedTextDigit(canvas, 3, d3, slot3X, baselineY, poppinsPaint, slotW, textHeight)

        canvas.drawText(amPmText, amPmX, baselineY, poppinsAmPmPaint)
    }

    // ── Theme 1: Nothing 5x7 Dot Matrix Clock ────────────────────
    private fun drawDotMatrixClock(
        canvas: Canvas,
        w: Float,
        h: Float,
        d1: Int,
        d2: Int,
        d3: Int,
        d4: Int,
        period: String?,
        cal: Calendar
    ) {
        val isLandscape = w > h
        val density = resources.displayMetrics.density

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
        val colonSpacing = max(6f * density, dotSize * 1.55f)

        // Fixed grid positions for all 4 digits so dot matrix never shifts horizontally
        val totalGridWidth = (digitWidth * 4f) + (digitGap * 2f) + (colonSpacing * 2f)
        val startX = (w - totalGridWidth) / 2f

        val x1 = startX
        val x2 = x1 + digitWidth + digitGap
        val finalColonCenterX = x2 + digitWidth + colonSpacing
        val x3 = finalColonCenterX + colonSpacing
        val x4 = x3 + digitWidth + digitGap

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

        var headerLeftX = x1
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
        val periodX = (x4 + digitWidth) - periodW
        canvas.drawText(periodText, periodX, headerY, periodPaint)

        // Draw Digits
        drawAnimatedDotDigit(canvas, 0, d1, x1, startY, dotSize, gap, digitWidth, digitHeight)
        drawAnimatedDotDigit(canvas, 1, d2, x2, startY, dotSize, gap, digitWidth, digitHeight)

        // Draw Colon
        val colonR = dotRadius
        val dot1CenterY = startY + (2 * (dotSize + gap)) + colonR
        val dot2CenterY = startY + (4 * (dotSize + gap)) + colonR
        canvas.drawCircle(finalColonCenterX, dot1CenterY, colonR, colonPaint)
        canvas.drawCircle(finalColonCenterX, dot2CenterY, colonR, colonPaint)

        drawAnimatedDotDigit(canvas, 2, d3, x3, startY, dotSize, gap, digitWidth, digitHeight)
        drawAnimatedDotDigit(canvas, 3, d4, x4, startY, dotSize, gap, digitWidth, digitHeight)
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
