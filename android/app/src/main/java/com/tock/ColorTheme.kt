package com.tock

import android.graphics.Color

enum class ThemeStyle {
    DOT_MATRIX,
    POPPINS
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
            name = "dot dot done",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#FCFAFE"),
            colonColor = Color.parseColor("#D71921"),
            metaColor = Color.parseColor("#FCFAFE"),
            periodColor = Color.parseColor("#D71921"),
            style = ThemeStyle.DOT_MATRIX
        )

        val POPPINS = ColorTheme(
            id = "poppins",
            name = "no drama",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#FCFAFE"),
            colonColor = Color.parseColor("#FCFAFE"),
            metaColor = Color.parseColor("#FCFAFE"),
            periodColor = Color.parseColor("#FFFFFF"),
            style = ThemeStyle.POPPINS
        )

        val ALL = listOf(CLASSIC, POPPINS)

        fun byId(id: String): ColorTheme = ALL.firstOrNull { it.id == id } ?: CLASSIC
    }
}
