package com.boop

import android.content.Context
import android.content.SharedPreferences

object BoopPreferences {
    private const val PREFS_NAME = "boop_prefs"

    private const val KEY_THEME = "theme"
    private const val KEY_USE_24H = "use_24h"
    private const val KEY_DND_ENABLED = "dnd_enabled"
    private const val KEY_BURN_IN_PROTECTION = "burn_in_protection"
    private const val KEY_LOW_POWER = "low_power"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTheme(ctx: Context): String = prefs(ctx).getString(KEY_THEME, "classic") ?: "classic"
    fun setTheme(ctx: Context, theme: String) = prefs(ctx).edit().putString(KEY_THEME, theme).apply()

    fun is24Hour(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_USE_24H, true)
    fun set24Hour(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_USE_24H, v).apply()

    fun isDndEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DND_ENABLED, true)
    fun setDndEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_DND_ENABLED, v).apply()

    fun isBurnInProtection(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BURN_IN_PROTECTION, true)
    fun setBurnInProtection(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_BURN_IN_PROTECTION, v).apply()

    fun isLowPower(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_LOW_POWER, false)
    fun setLowPower(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_LOW_POWER, v).apply()
}
