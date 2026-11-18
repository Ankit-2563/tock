package com.tock

import android.graphics.Color

enum class ThemeStyle {
    DOT_MATRIX,
    CHUNKY_BLUE,
    BUBBLE_PURPLE,
    POPPINS,
    CONDENSED_ORANGE
}

data class ColorTheme(
    val id: String,
    val name: String,
    val background: Int,
    val digitColor: Int,
    val colonColor: Int,
    val metaColor: Int,
    val periodColor: Int,
    val style: ThemeStyle = ThemeStyle.DOT_MATRIX
) {
    companion object {
        val CLASSIC = ColorTheme(
            id = "classic",
            name = "Nothing Dot Matrix",
            background = Color.parseColor("#141817"),
            digitColor = Color.parseColor("#FCFAFE"),
            colonColor = Color.parseColor("#D71921"),
            metaColor = Color.parseColor("#FCFAFE"),
            periodColor = Color.parseColor("#D71921"),
            style = ThemeStyle.DOT_MATRIX
        )

        val CHUNKY_BLUE = ColorTheme(
            id = "chunky_blue",
            name = "Chunky Electric Blue",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#0084FF"),
            colonColor = Color.parseColor("#FFFFFF"),
            metaColor = Color.parseColor("#0084FF"),
            periodColor = Color.parseColor("#FFFFFF"),
            style = ThemeStyle.CHUNKY_BLUE
        )

        val BUBBLE_PURPLE = ColorTheme(
            id = "bubble_purple",
            name = "Funky Bubble Purple",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#D8639F"),
            colonColor = Color.parseColor("#FFFFFF"),
            metaColor = Color.parseColor("#9D90F8"),
            periodColor = Color.parseColor("#FFFFFF"),
            style = ThemeStyle.BUBBLE_PURPLE
        )

        val POPPINS = ColorTheme(
            id = "poppins",
            name = "Poppins Clean",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#FCFAFE"),
            colonColor = Color.parseColor("#FCFAFE"),
            metaColor = Color.parseColor("#FCFAFE"),
            periodColor = Color.parseColor("#FFFFFF"),
            style = ThemeStyle.POPPINS
        )

        val CONDENSED_ORANGE = ColorTheme(
            id = "condensed_orange",
            name = "Condensed Retro Orange",
            background = Color.parseColor("#F7F6F8"),
            digitColor = Color.parseColor("#F2380C"),
            colonColor = Color.parseColor("#F2380C"),
            metaColor = Color.parseColor("#F2380C"),
            periodColor = Color.parseColor("#F2380C"),
            style = ThemeStyle.CONDENSED_ORANGE
        )

        val OLED = ColorTheme(
            id = "oled",
            name = "OLED Pure Black",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#FCFAFE"),
            colonColor = Color.parseColor("#D71921"),
            metaColor = Color.parseColor("#FCFAFE"),
            periodColor = Color.parseColor("#D71921"),
            style = ThemeStyle.DOT_MATRIX
        )

        val NIGHT = ColorTheme(
            id = "night",
            name = "StandBy Red",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#D71921"),
            colonColor = Color.parseColor("#D71921"),
            metaColor = Color.parseColor("#D71921"),
            periodColor = Color.parseColor("#D71921"),
            style = ThemeStyle.DOT_MATRIX
        )

        val MONO = ColorTheme(
            id = "monochrome",
            name = "Monochrome",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#FCFAFE"),
            colonColor = Color.parseColor("#FCFAFE"),
            metaColor = Color.parseColor("#FCFAFE"),
            periodColor = Color.parseColor("#FCFAFE"),
            style = ThemeStyle.DOT_MATRIX
        )

        val AMBER = ColorTheme(
            id = "amber",
            name = "Warm Amber",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#FFA726"),
            colonColor = Color.parseColor("#FF7043"),
            metaColor = Color.parseColor("#FFA726"),
            periodColor = Color.parseColor("#FF7043"),
            style = ThemeStyle.DOT_MATRIX
        )

        val EMERALD = ColorTheme(
            id = "emerald",
            name = "Matrix Green",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#4ADE80"),
            colonColor = Color.parseColor("#22C55E"),
            metaColor = Color.parseColor("#4ADE80"),
            periodColor = Color.parseColor("#22C55E"),
            style = ThemeStyle.DOT_MATRIX
        )

        val ALL = listOf(CLASSIC, CHUNKY_BLUE, BUBBLE_PURPLE, POPPINS, CONDENSED_ORANGE, OLED, NIGHT, MONO, AMBER, EMERALD)

        fun byId(id: String): ColorTheme = ALL.firstOrNull { it.id == id } ?: CLASSIC
    }
}
