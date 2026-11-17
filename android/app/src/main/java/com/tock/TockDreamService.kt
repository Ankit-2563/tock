package com.tock

import android.app.NotificationManager
import android.content.Context
import android.service.dreams.DreamService
import android.view.WindowManager

/**
 * Android Screen Saver (DreamService).
 * Selected in Settings → Display → Screen Saver.
 * Activates automatically and natively when the device is idle and charging.
 */
class TockDreamService : DreamService() {

    private lateinit var clockView: TockClockView
    private var dndWasEnabled = false
    private var previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        isScreenBright = !TockPreferences.isLowPower(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        clockView = TockClockView(this)
        val theme = ColorTheme.byId(TockPreferences.getTheme(this))
        clockView.setTheme(theme)
        clockView.set24Hour(TockPreferences.is24Hour(this))
        clockView.setBurnInProtection(TockPreferences.isBurnInProtection(this))
        clockView.setLowPower(TockPreferences.isLowPower(this))

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
        if (!TockPreferences.isDndEnabled(this)) return
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
