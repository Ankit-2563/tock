package com.boop

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
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsBottomSheet : BottomSheetDialogFragment() {

    var onSettingsChanged: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

        // ── Title ──────────────────────────────────────────────
        val titleView = TextView(ctx).apply {
            text = "BOOP SETTINGS"
            typeface = nothingFont
            textSize = 22f
            setTextColor(Color.parseColor("#FCFAFE"))
            setPadding(0, 0, 0, 32)
        }
        content.addView(titleView)

        // ── Themes Section ─────────────────────────────────────
        val themeHeader = TextView(ctx).apply {
            text = "THEMES"
            typeface = nothingFont
            textSize = 14f
            setTextColor(Color.parseColor("#8A8D8C"))
            setPadding(0, 8, 0, 16)
        }
        content.addView(themeHeader)

        val themeScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 32)
        }
        val themeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        themeScroll.addView(themeRow)

        val currentThemeId = BoopPreferences.getTheme(ctx)

        for (t in ColorTheme.ALL) {
            val isSelected = t.id == currentThemeId
            val themeBtn = TextView(ctx).apply {
                text = t.name
                textSize = 13f
                setPadding(28, 18, 28, 18)
                val bg = GradientDrawable().apply {
                    cornerRadius = 24f
                    if (isSelected) {
                        setColor(Color.parseColor("#D71921"))
                        setStroke(2, Color.WHITE)
                    } else {
                        setColor(Color.parseColor("#1E2221"))
                        setStroke(1, Color.parseColor("#333333"))
                    }
                }
                background = bg
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#FCFAFE"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 16, 0) }

                setOnClickListener {
                    BoopPreferences.setTheme(ctx, t.id)
                    onSettingsChanged?.invoke()
                    dismiss()
                }
            }
            themeRow.addView(themeBtn)
        }
        content.addView(themeScroll)

        // ── Divider ────────────────────────────────────────────
        fun addDivider() {
            val div = View(ctx).apply {
                setBackgroundColor(Color.parseColor("#222625"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 2
                ).apply { setMargins(0, 16, 0, 16) }
            }
            content.addView(div)
        }

        // ── Switch Row Helper ──────────────────────────────────
        fun addSwitchRow(title: String, subtitle: String, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 16, 0, 16)
            }
            val textLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleLabel = TextView(ctx).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#FCFAFE"))
            }
            val subLabel = TextView(ctx).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#8A8D8C"))
                setPadding(0, 4, 0, 0)
            }
            textLayout.addView(titleLabel)
            textLayout.addView(subLabel)

            val toggle = Switch(ctx).apply {
                this.isChecked = isChecked
                setOnCheckedChangeListener { _, checked ->
                    onToggle(checked)
                    onSettingsChanged?.invoke()
                }
            }

            row.addView(textLayout)
            row.addView(toggle)
            content.addView(row)
        }

        addDivider()

        // 24-Hour Format
        addSwitchRow(
            "24-Hour Clock",
            "Display 24-hour time or 12-hour with AM/PM",
            BoopPreferences.is24Hour(ctx)
        ) { BoopPreferences.set24Hour(ctx, it) }

        addDivider()

        // Auto-Launch on Charge + Horizontal
        addSwitchRow(
            "Auto-Activate on Charge",
            "Launches Boop when charger connected in horizontal dock/idle position",
            BoopPreferences.isAutoLaunch(ctx)
        ) { BoopPreferences.setAutoLaunch(ctx, it) }

        addDivider()

        // Do Not Disturb
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasDnd = nm.isNotificationPolicyAccessGranted
        addSwitchRow(
            "Do Not Disturb",
            if (hasDnd) "Silence notifications while Boop is active" else "Requires permission to silence notifications (tap to grant)",
            BoopPreferences.isDndEnabled(ctx)
        ) {
            if (!hasDnd && it) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            BoopPreferences.setDndEnabled(ctx, it)
        }

        addDivider()

        // Burn-in Protection
        addSwitchRow(
            "OLED Burn-in Protection",
            "Shifts pixels subtly every 60 seconds to protect AMOLED screens",
            BoopPreferences.isBurnInProtection(ctx)
        ) { BoopPreferences.setBurnInProtection(ctx, it) }

        addDivider()

        // Low Power Mode
        addSwitchRow(
            "Low Power Mode",
            "Dims display to minimal brightness and reduces refresh rate",
            BoopPreferences.isLowPower(ctx)
        ) { BoopPreferences.setLowPower(ctx, it) }

        addDivider()

        // ── Screen Saver Button ────────────────────────────────
        val screenSaverBtn = Button(ctx).apply {
            text = "Set as System Screen Saver (Dock Mode)"
            setBackgroundColor(Color.parseColor("#1E2221"))
            setTextColor(Color.parseColor("#FCFAFE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 24, 0, 0) }
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                }
            }
        }
        content.addView(screenSaverBtn)

        return root
    }
}
