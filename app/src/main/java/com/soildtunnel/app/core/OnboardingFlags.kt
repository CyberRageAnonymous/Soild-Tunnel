package com.soildtunnel.app.core

import android.content.Context
import android.content.SharedPreferences

/** One-shot onboarding flags (shown once per install, then never again). */
object OnboardingFlags {

    private const val PREFS_NAME = "onboarding_flags"
    private const val KEY_TELEGRAM_BANNER = "telegram_banner_seen"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun telegramBannerSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TELEGRAM_BANNER, false)

    fun markTelegramBannerSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_TELEGRAM_BANNER, true).apply()
    }
}
