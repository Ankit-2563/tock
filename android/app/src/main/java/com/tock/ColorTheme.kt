package com.tock

import android.graphics.Color

enum class ThemeStyle {
    DOT_MATRIX,
    POPPINS,
    TOCK_BLOOM,
    CANDY_CLOCK
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

        val TOCK_BLOOM = ColorTheme(
            id = "tock_bloom",
            name = "tock bloom",
            background = Color.parseColor("#F8F7F9"),
            digitColor = Color.parseColor("#EB3C11"),
            colonColor = Color.parseColor("#EB3C11"),
            metaColor = Color.parseColor("#EB3C11"),
            periodColor = Color.parseColor("#EB3C11"),
            style = ThemeStyle.TOCK_BLOOM
        )

        val CANDY_CLOCK = ColorTheme(
            id = "candy_clock",
            name = "candy clock",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#D170B3"),
            colonColor = Color.parseColor("#E6E6E6"),
            metaColor = Color.parseColor("#E6E6E6"),
            periodColor = Color.parseColor("#E6E6E6"),
            style = ThemeStyle.CANDY_CLOCK
        )

        val ALL = listOf(CLASSIC, POPPINS, TOCK_BLOOM, CANDY_CLOCK)

        fun byId(id: String): ColorTheme = ALL.firstOrNull { it.id == id } ?: CLASSIC
    }
}
