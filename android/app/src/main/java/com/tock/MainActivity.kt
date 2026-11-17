package com.tock

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var clockView: TockClockView
    private var dndWasEnabled = false
    private var previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on forever
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Show on lock screen and turn screen on across all Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // Create clock view
        clockView = TockClockView(this)
        setContentView(clockView)

        // Apply saved settings
        refreshSettings()

        // Long press or tap anywhere opens settings
        clockView.onOpenSettingsListener = {
            val sheet = SettingsBottomSheet()
            sheet.onSettingsChanged = {
                refreshSettings()
            }
            sheet.show(supportFragmentManager, "settings")
        }

        // Enter immersive mode
        enterImmersive()

        // Enable DND
        enableDnd()
    }

    private fun refreshSettings() {
        val theme = ColorTheme.byId(TockPreferences.getTheme(this))
        clockView.setTheme(theme)
        clockView.set24Hour(TockPreferences.is24Hour(this))
        clockView.setBurnInProtection(TockPreferences.isBurnInProtection(this))
        clockView.setLowPower(TockPreferences.isLowPower(this))

        val lp = window.attributes
        if (TockPreferences.isLowPower(this)) {
            lp.screenBrightness = 0.01f
        } else {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = lp
    }

    override fun onResume() {
        super.onResume()
        enterImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun enterImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ctrl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun enableDnd() {
        if (!TockPreferences.isDndEnabled(this)) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return
        }
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

    override fun onDestroy() {
        disableDnd()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        disableDnd()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
