package com.boop

import android.service.dreams.DreamService
import android.view.WindowManager

/**
 * Android Screen Saver (DreamService).
 * Users can select this in Settings → Display → Screen Saver.
 * It activates automatically when the device is docked/charging.
 */
class BoopDreamService : DreamService() {

    private lateinit var clockView: BoopClockView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        isScreenBright = !BoopPreferences.isLowPower(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        clockView = BoopClockView(this)
        val theme = ColorTheme.byId(BoopPreferences.getTheme(this))
        clockView.setTheme(theme)
        clockView.set24Hour(BoopPreferences.is24Hour(this))
        clockView.setBurnInProtection(BoopPreferences.isBurnInProtection(this))
        clockView.setLowPower(BoopPreferences.isLowPower(this))

        setContentView(clockView)
    }
}
