package com.tock

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private enum class Tab { THEMES, FAVS }
    private var currentTab = Tab.THEMES

    private lateinit var rootLayout: FrameLayout
    private lateinit var header: TextView
    private lateinit var cardsContainer: LinearLayout
    private lateinit var tabThemesBtn: TextView
    private lateinit var tabFavsBtn: TextView

    private val cardViews = mutableListOf<ThemePreviewCard>()
    private val easeInOut = AccelerateDecelerateInterpolator()

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_TIME_TICK) {
                val cal = Calendar.getInstance()
                val minute = cal.get(Calendar.MINUTE)
                if (minute == 0) {
                    TockAudioPlayer.playHourChime(context)
                } else {
                    TockAudioPlayer.playMinuteTick(context)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TockAudioPlayer.init(this)

        val density = resources.displayMetrics.density
        val margin8 = (8 * density).toInt()

        // Root FrameLayout: background #EBEAEB
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#EBEAEB"))
        }

        // Scrollable content
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            setPadding(0, 0, 0, (88 * density).toInt()) // Space for bottom pill bar
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView, FrameLayout.LayoutParams(-1, -1))

        // Top Header: "Themes" or "Favorites"
        header = TextView(this).apply {
            text = "Themes"
            textSize = 34f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(Color.parseColor("#1C1E1D"))
            setPadding(margin8 * 2, (36 * density).toInt(), margin8 * 2, (16 * density).toInt())
        }
        contentLayout.addView(header)

        // Cards Container
        cardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentLayout.addView(cardsContainer)

        // Bottom Floating Navigation Bar (Pill buttons: "Themes" and "Favs")
        val bottomBar = createBottomPillBar()
        val bottomParams = FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(margin8 * 2, 0, 0, (24 * density).toInt())
        }
        rootLayout.addView(bottomBar, bottomParams)

        setContentView(rootLayout)

        // Populate cards
        renderThemeCards()
    }

    override fun onResume() {
        super.onResume()
        try {
            registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        } catch (_: Exception) {}
        renderThemeCards()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(timeTickReceiver)
        } catch (_: Exception) {}
    }

    private fun renderThemeCards() {
        cardsContainer.removeAllViews()
        cardViews.clear()

        val density = resources.displayMetrics.density
        val margin8 = (8 * density).toInt()
        val cardHeight = (168 * density).toInt()

        val activeThemeId = TockPreferences.getTheme(this)
        val favorites = TockPreferences.getFavorites(this)

        val displayedThemes = if (currentTab == Tab.FAVS) {
            ColorTheme.ALL.filter { favorites.contains(it.id) }
        } else {
            ColorTheme.ALL
        }

        if (displayedThemes.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No favorites yet.\nTap the star on any theme to add it here!"
                textSize = 15f
                setTextColor(Color.parseColor("#7A7E7D"))
                gravity = Gravity.CENTER
                setPadding(0, (60 * density).toInt(), 0, 0)
            }
            cardsContainer.addView(emptyView)
            return
        }

        for (theme in displayedThemes) {
            val isSelected = theme.id == activeThemeId
            val isFav = favorites.contains(theme.id)

            val card = ThemePreviewCard(
                context = this,
                theme = theme,
                isSelectedTheme = isSelected,
                isFavoriteTheme = isFav
            ).apply {
                onCardClickListener = {
                    animateCardPress(this) {
                        TockPreferences.setTheme(this@MainActivity, theme.id)
                        updateActiveCards(theme.id)
                        // Launch into fullscreen StandBy clock
                        val intent = Intent(this@MainActivity, ClockActivity::class.java)
                        startActivity(intent)
                        @Suppress("DEPRECATION")
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                }

                onFavoriteToggleListener = { fav ->
                    TockPreferences.toggleFavorite(this@MainActivity, theme.id)
                    val toastMsg = if (fav) "Added to Favorites" else "Removed from Favorites"
                    Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_SHORT).show()
                    if (currentTab == Tab.FAVS && !fav) {
                        renderThemeCards()
                    }
                }
            }

            val cardParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                cardHeight
            ).apply {
                setMargins(margin8, margin8, margin8, 0)
            }

            cardsContainer.addView(card, cardParams)
            cardViews.add(card)
        }
    }

    private fun updateActiveCards(selectedThemeId: String) {
        for (card in cardViews) {
            card.setSelectedState(card.theme.id == selectedThemeId)
        }
    }

    private fun createBottomPillBar(): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Pill 1: Themes
        tabThemesBtn = TextView(this).apply {
            text = "Themes"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding((24 * density).toInt(), (14 * density).toInt(), (24 * density).toInt(), (14 * density).toInt())
            setOnClickListener {
                switchTab(Tab.THEMES)
            }
        }

        // Pill 2: Favs
        tabFavsBtn = TextView(this).apply {
            text = "Favs"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding((24 * density).toInt(), (14 * density).toInt(), (24 * density).toInt(), (14 * density).toInt())
            val lp = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins((10 * density).toInt(), 0, 0, 0)
            }
            layoutParams = lp
            setOnClickListener {
                switchTab(Tab.FAVS)
            }
        }

        row.addView(tabThemesBtn)
        row.addView(tabFavsBtn)
        updatePillStyles()

        return row
    }

    /** Switch tabs with silky ease-in and ease-out transitions for buttons and cards. */
    private fun switchTab(newTab: Tab) {
        if (currentTab == newTab) {
            val btn = if (newTab == Tab.THEMES) tabThemesBtn else tabFavsBtn
            animateMicroPress(btn)
            return
        }

        currentTab = newTab

        // 1. Animate Pill button styles with Ease-In-Out
        animatePillSwitch(newTab)

        // 2. Animate header text change with Ease-In-Out crossfade
        val targetHeaderText = if (newTab == Tab.THEMES) "Themes" else "Favorites"
        header.animate()
            .alpha(0f)
            .setDuration(120)
            .setInterpolator(easeInOut)
            .withEndAction {
                header.text = targetHeaderText
                header.animate()
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(easeInOut)
                    .start()
            }
            .start()

        // 3. Smooth Ease-In-Out transition for cards container (fade out then fade in)
        cardsContainer.animate()
            .alpha(0f)
            .translationY(-12f)
            .setDuration(140)
            .setInterpolator(easeInOut)
            .withEndAction {
                renderThemeCards()
                cardsContainer.translationY = 16f
                cardsContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(easeInOut)
                    .start()
            }
            .start()
    }

    private fun updatePillStyles() {
        val density = resources.displayMetrics.density
        val corner = 24f * density

        if (currentTab == Tab.THEMES) {
            tabThemesBtn.setTextColor(Color.WHITE)
            tabThemesBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#181C1B"))
                cornerRadius = corner
            }
            tabFavsBtn.setTextColor(Color.parseColor("#262A29"))
            tabFavsBtn.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = corner
            }
        } else {
            tabThemesBtn.setTextColor(Color.parseColor("#262A29"))
            tabThemesBtn.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = corner
            }
            tabFavsBtn.setTextColor(Color.WHITE)
            tabFavsBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#181C1B"))
                cornerRadius = corner
            }
        }
    }

    /** Animated pill color/text morphing using Ease-In-Out curve. */
    private fun animatePillSwitch(selectedTab: Tab) {
        val density = resources.displayMetrics.density
        val corner = 24f * density
        val darkBg = Color.parseColor("#181C1B")
        val whiteBg = Color.WHITE
        val darkText = Color.parseColor("#262A29")
        val whiteText = Color.WHITE

        val activeBtn = if (selectedTab == Tab.THEMES) tabThemesBtn else tabFavsBtn
        val inactiveBtn = if (selectedTab == Tab.THEMES) tabFavsBtn else tabThemesBtn

        // Subtle tactile press & spring on active button with ease-in-out
        activeBtn.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(110)
            .setInterpolator(easeInOut)
            .withEndAction {
                activeBtn.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(190)
                    .setInterpolator(easeInOut)
                    .start()
            }
            .start()

        // Morph pill colors smoothly over 300ms using Ease-In-Out
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = easeInOut
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                val activeBg = blendColors(whiteBg, darkBg, f)
                val activeTxt = blendColors(darkText, whiteText, f)
                activeBtn.setTextColor(activeTxt)
                activeBtn.background = GradientDrawable().apply {
                    setColor(activeBg)
                    cornerRadius = corner
                }

                val inactiveBg = blendColors(darkBg, whiteBg, f)
                val inactiveTxt = blendColors(whiteText, darkText, f)
                inactiveBtn.setTextColor(inactiveTxt)
                inactiveBtn.background = GradientDrawable().apply {
                    setColor(inactiveBg)
                    cornerRadius = corner
                }
            }
            start()
        }
    }

    /** Subtle tactile feedback when tapping currently active tab. */
    private fun animateMicroPress(view: View) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(90)
            .setInterpolator(easeInOut)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160)
                    .setInterpolator(easeInOut)
                    .start()
            }
            .start()
    }

    /** Linear color blend between two ARGB ints. */
    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val a = (Color.alpha(from) * inv + Color.alpha(to) * ratio).toInt()
        val r = (Color.red(from) * inv + Color.red(to) * ratio).toInt()
        val g = (Color.green(from) * inv + Color.green(to) * ratio).toInt()
        val b = (Color.blue(from) * inv + Color.blue(to) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }

    /** Smooth Ease-In-Out press feedback for theme cards. */
    private fun animateCardPress(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(110)
            .setInterpolator(easeInOut)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(easeInOut)
                    .start()
                action()
            }
            .start()
    }
}
