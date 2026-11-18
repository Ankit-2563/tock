package com.tock

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private enum class Tab { THEMES, FAVS }
    private var currentTab = Tab.THEMES

    private lateinit var rootLayout: FrameLayout
    private lateinit var cardsContainer: LinearLayout
    private lateinit var tabThemesBtn: TextView
    private lateinit var tabFavsBtn: TextView

    private val cardViews = mutableListOf<ThemePreviewCard>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val margin8 = (8 * density).toInt()   // 8px margin and gutter as specified
        val rowHeight8 = (8 * density).toInt() // 8px row height

        // Root FrameLayout: background #EBEBEB matching screenshot
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

        // Top Header: "Themes"
        val header = TextView(this).apply {
            text = "Themes"
            textSize = 34f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(Color.parseColor("#1C1E1D"))
            // Margin 8px left aligned with cards, 40dp top padding
            setPadding(margin8 * 2, (36 * density).toInt(), margin8 * 2, (16 * density).toInt())
        }
        contentLayout.addView(header)

        // Cards Container: 4-column layout with 8px margin & gutter
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
        renderThemeCards()
    }

    private fun renderThemeCards() {
        cardsContainer.removeAllViews()
        cardViews.clear()

        val density = resources.displayMetrics.density
        val margin8 = (8 * density).toInt()
        val cardHeight = (168 * density).toInt() // Exactly 21 rows of 8px, maintaining ~2.15:1 clock aspect ratio

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
                    TockPreferences.setTheme(this@MainActivity, theme.id)
                    updateActiveCards(theme.id)
                    // Launch into fullscreen StandBy clock
                    val intent = Intent(this@MainActivity, ClockActivity::class.java)
                    startActivity(intent)
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
                // 8px margin left/right, 8px gutter vertical
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
            setPadding((24 * density).toInt(), (14 * density).toInt(), (24 * density).toInt(), (14 * density).toInt())
            setOnClickListener {
                if (currentTab != Tab.THEMES) {
                    currentTab = Tab.THEMES
                    updatePillStyles()
                    renderThemeCards()
                }
            }
        }

        // Pill 2: Favs
        tabFavsBtn = TextView(this).apply {
            text = "Favs"
            textSize = 14f
            setPadding((24 * density).toInt(), (14 * density).toInt(), (24 * density).toInt(), (14 * density).toInt())
            val lp = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins((10 * density).toInt(), 0, 0, 0)
            }
            layoutParams = lp
            setOnClickListener {
                if (currentTab != Tab.FAVS) {
                    currentTab = Tab.FAVS
                    updatePillStyles()
                    renderThemeCards()
                }
            }
        }

        row.addView(tabThemesBtn)
        row.addView(tabFavsBtn)
        updatePillStyles()

        return row
    }

    private fun updatePillStyles() {
        val density = resources.displayMetrics.density
        val corner = 24f * density

        // Themes Pill
        if (currentTab == Tab.THEMES) {
            tabThemesBtn.setTextColor(Color.WHITE)
            tabThemesBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#181C1B"))
                cornerRadius = corner
            }
        } else {
            tabThemesBtn.setTextColor(Color.parseColor("#262A29"))
            tabThemesBtn.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = corner
            }
        }

        // Favs Pill
        if (currentTab == Tab.FAVS) {
            tabFavsBtn.setTextColor(Color.WHITE)
            tabFavsBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#181C1B"))
                cornerRadius = corner
            }
        } else {
            tabFavsBtn.setTextColor(Color.parseColor("#262A29"))
            tabFavsBtn.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = corner
            }
        }
    }
}
