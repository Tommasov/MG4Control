package com.mg4.control

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.mg4.control.util.LocaleHelper
import com.mg4.control.util.ThemeHelper

class MG4App : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()

        // ── Migration + initialisation du thème ───────────────────────────────
        val prefs = getSharedPreferences("mg4_settings", Context.MODE_PRIVATE)

        if (!prefs.contains(ThemeHelper.PREF_THEME_MODE)) {
            // Migration depuis l'ancien booléen "dark_theme" (version < 2.x)
            // Sur nouvelle installation : "auto" (tous les firmwares le supportent)
            val defaultMode = when {
                prefs.contains("dark_theme") ->
                    if (prefs.getBoolean("dark_theme", true)) "dark" else "light"
                else -> "auto"
            }
            prefs.edit().putString(ThemeHelper.PREF_THEME_MODE, defaultMode).apply()
        }

        AppCompatDelegate.setDefaultNightMode(ThemeHelper.resolveNightMode(this))
    }
}
