package com.boop

import android.graphics.Color

data class ColorTheme(
    val id: String,
    val name: String,
    val background: Int,
    val digitColor: Int,
    val colonColor: Int,
    val metaColor: Int,
    val periodColor: Int
) {
    companion object {
        val CLASSIC = ColorTheme(
            id = "classic",
            name = "Nothing Classic (Theme 1)",
            background = Color.parseColor("#1A1D1C"),
            digitColor = Color.parseColor("#FCFAFE"),
            colonColor = Color.parseColor("#D71921"),
            metaColor = Color.parseColor("#FCFAFE"),
            periodColor = Color.parseColor("#D71921")
        )
        val OLED = ColorTheme(
            id = "oled",
            name = "OLED Pure Black",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#FCFAFE"),
            colonColor = Color.parseColor("#D71921"),
            metaColor = Color.parseColor("#FCFAFE"),
            periodColor = Color.parseColor("#D71921")
        )
        val NIGHT = ColorTheme(
            id = "night",
            name = "StandBy Red",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#D71921"),
            colonColor = Color.parseColor("#D71921"),
            metaColor = Color.parseColor("#D71921"),
            periodColor = Color.parseColor("#D71921")
        )
        val MONO = ColorTheme(
            id = "monochrome",
            name = "Monochrome",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#FCFAFE"),
            colonColor = Color.parseColor("#FCFAFE"),
            metaColor = Color.parseColor("#FCFAFE"),
            periodColor = Color.parseColor("#FCFAFE")
        )
        val AMBER = ColorTheme(
            id = "amber",
            name = "Warm Amber",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#FFA726"),
            colonColor = Color.parseColor("#FF7043"),
            metaColor = Color.parseColor("#FFA726"),
            periodColor = Color.parseColor("#FF7043")
        )
        val EMERALD = ColorTheme(
            id = "emerald",
            name = "Matrix Green",
            background = Color.parseColor("#000000"),
            digitColor = Color.parseColor("#4ADE80"),
            colonColor = Color.parseColor("#22C55E"),
            metaColor = Color.parseColor("#4ADE80"),
            periodColor = Color.parseColor("#22C55E")
        )

        val ALL = listOf(CLASSIC, OLED, NIGHT, MONO, AMBER, EMERALD)

        fun byId(id: String): ColorTheme = ALL.firstOrNull { it.id == id } ?: CLASSIC
    }
}
