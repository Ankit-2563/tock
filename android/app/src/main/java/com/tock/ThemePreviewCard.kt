package com.tock

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class ThemePreviewCard(
    context: Context,
    val theme: ColorTheme,
    var isSelectedTheme: Boolean = false,
    var isFavoriteTheme: Boolean = false
) : FrameLayout(context) {

    private val clockView = TockClockView(context).apply {
        isPreview = true
        mockTime = Pair(20, 2)
        setTheme(theme)
    }

    private val selectBadge: TextView
    private val favIcon: ImageView

    var onCardClickListener: (() -> Unit)? = null
    var onFavoriteToggleListener: ((Boolean) -> Unit)? = null

    init {
        val density = resources.displayMetrics.density
        val radius = 28f * density // 28dp border radius as requested

        // Clip to 28dp rounded rectangle
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true

        // Background
        setBackgroundColor(theme.background)
        contentDescription = theme.name

        // Add the mini clock view inside the card
        addView(clockView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Selected indicator badge (top left)
        selectBadge = TextView(context).apply {
            text = "ACTIVE"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E01A22"))
                cornerRadius = 12f * density
            }
            visibility = if (isSelectedTheme) View.VISIBLE else View.GONE
        }
        val badgeLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins((16 * density).toInt(), (16 * density).toInt(), 0, 0)
        }
        addView(selectBadge, badgeLp)

        // Favorite heart icon (top right)
        favIcon = ImageView(context).apply {
            val size = (32 * density).toInt()
            layoutParams = LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, (14 * density).toInt(), (14 * density).toInt(), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#44000000"))
            }
            setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
            setImageResource(if (isFavoriteTheme) android.R.drawable.star_big_on else android.R.drawable.star_big_off)
            setOnClickListener {
                isFavoriteTheme = !isFavoriteTheme
                setImageResource(if (isFavoriteTheme) android.R.drawable.star_big_on else android.R.drawable.star_big_off)
                onFavoriteToggleListener?.invoke(isFavoriteTheme)
            }
        }
        addView(favIcon)

        // Click on card
        setOnClickListener {
            onCardClickListener?.invoke()
        }

        // Selected border styling
        updateSelectionStyle()
    }

    fun setSelectedState(selected: Boolean) {
        isSelectedTheme = selected
        selectBadge.visibility = if (selected) View.VISIBLE else View.GONE
        updateSelectionStyle()
    }

    private fun updateSelectionStyle() {
        val density = resources.displayMetrics.density
        foreground = if (isSelectedTheme) {
            GradientDrawable().apply {
                cornerRadius = 28f * density
                setStroke((3 * density).toInt(), Color.parseColor("#D71921"))
            }
        } else {
            null
        }
    }
}
