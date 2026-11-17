package com.boop

import android.app.NotificationManager
import android.content.Context
import android.service.dreams.DreamService
import android.view.WindowManager

/**
 * Android Screen Saver (DreamService).
 * Selected in Settings → Display → Screen Saver.
 * Activates automatically and natively when the device is idle and charging.
 */
class BoopDreamService : DreamService() {

    private lateinit var clockView: BoopClockView
    private var dndWasEnabled = false
    private var previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL

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

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        enableDnd()
    }

    override fun onDreamingStopped() {
        disableDnd()
        super.onDreamingStopped()
    }

    private fun enableDnd() {
        if (!BoopPreferences.isDndEnabled(this)) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        previousInterruptionFilter = nm.currentInterruptionFilter
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        dndWasEnabled = true
    }

    private fun disableDnd() {
        if (!dndWasEnabled) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(previousInterruptionFilter)
        }
        dndWasEnabled = false
    }
}
