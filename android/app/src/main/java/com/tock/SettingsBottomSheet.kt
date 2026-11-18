package com.tock

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsBottomSheet : BottomSheetDialogFragment() {

    var onSettingsChanged: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val nothingFont = Typeface.createFromAsset(ctx.assets, "fonts/NothingFont5x7.ttf")

        val root = ScrollView(ctx).apply {
            setBackgroundColor(Color.parseColor("#111413"))
            isFillViewport = true
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 60)
        }
        root.addView(content)

        // Title
        content.addView(TextView(ctx).apply {
            text = "TOCK SETTINGS"
            typeface = nothingFont
            textSize = 22f
            setTextColor(Color.parseColor("#FCFAFE"))
            setPadding(0, 0, 0, 24)
        })

        // Themes
        content.addView(TextView(ctx).apply {
            text = "THEMES"
            typeface = nothingFont
            textSize = 14f
            setTextColor(Color.parseColor("#8A8D8C"))
            setPadding(0, 0, 0, 12)
        })

        val currentThemeId = TockPreferences.getTheme(ctx)
        val themeRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in ColorTheme.ALL) {
            val isSelected = t.id == currentThemeId
            themeRow.addView(TextView(ctx).apply {
                text = t.name
                textSize = 13f
                setPadding(28, 16, 28, 16)
                background = GradientDrawable().apply {
                    cornerRadius = 24f
                    setColor(if (isSelected) Color.parseColor("#D71921") else Color.parseColor("#1E2221"))
                    if (isSelected) setStroke(2, Color.WHITE) else setStroke(1, Color.parseColor("#333333"))
                }
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#FCFAFE"))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, 16, 0) }
                setOnClickListener {
                    TockPreferences.setTheme(ctx, t.id)
                    onSettingsChanged?.invoke()
                    dismiss()
                }
            })
        }
        content.addView(HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 24)
            addView(themeRow)
        })

        fun divider() = content.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#222625"))
            layoutParams = LinearLayout.LayoutParams(-1, 2).apply { setMargins(0, 12, 0, 12) }
        })

        fun switchRow(title: String, subtitle: String, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }
            val textLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(TextView(ctx).apply { text = title; textSize = 16f; setTextColor(Color.parseColor("#FCFAFE")) })
                addView(TextView(ctx).apply { text = subtitle; textSize = 12f; setTextColor(Color.parseColor("#8A8D8C")); setPadding(0, 4, 0, 0) })
            }
            row.addView(textLayout)
            row.addView(Switch(ctx).apply {
                this.isChecked = isChecked
                setOnCheckedChangeListener { _, c -> onToggle(c); onSettingsChanged?.invoke() }
            })
            content.addView(row)
        }

        divider()
        switchRow("24-Hour Clock", "Display 24-hour time or 12-hour with AM/PM", TockPreferences.is24Hour(ctx)) {
            TockPreferences.set24Hour(ctx, it)
        }

        divider()
        // Screen Saver Button
        content.addView(Button(ctx).apply {
            text = "★ Configure Screen Saver (Auto-Start on Charge)"
            setTextColor(Color.parseColor("#FCFAFE"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2221"))
                cornerRadius = 18f
                setStroke(2, Color.parseColor("#D71921"))
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8, 0, 8) }
            setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }
                catch (_: Exception) { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            }
        })
        content.addView(TextView(ctx).apply {
            text = "Select 'Tock StandBy Clock' → 'When to start: While charging' for native dock mode."
            textSize = 12f
            setTextColor(Color.parseColor("#8A8D8C"))
            setPadding(0, 0, 0, 12)
        })

        divider()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasDnd = nm.isNotificationPolicyAccessGranted
        switchRow("Do Not Disturb", if (hasDnd) "Silence notifications while active" else "Requires permission to silence (tap to grant)", TockPreferences.isDndEnabled(ctx)) {
            if (!hasDnd && it) startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            TockPreferences.setDndEnabled(ctx, it)
        }

        divider()
        switchRow("OLED Burn-in Protection", "Shifts pixels subtly every 60s to protect AMOLED", TockPreferences.isBurnInProtection(ctx)) {
            TockPreferences.setBurnInProtection(ctx, it)
        }

        divider()
        switchRow("Acoustic Chime & Ticks", "Plays a gentle 'tak-tak' on minutes and a warm chime on hours (tap to test)", TockPreferences.isSoundEnabled(ctx)) {
            TockPreferences.setSoundEnabled(ctx, it)
            if (it) {
                TockAudioPlayer.playMinuteTick(ctx)
            }
        }

        divider()
        switchRow("Low Power Mode", "Dims display brightness and reduces tick frequency", TockPreferences.isLowPower(ctx)) {
            TockPreferences.setLowPower(ctx, it)
        }

        return root
    }
}
