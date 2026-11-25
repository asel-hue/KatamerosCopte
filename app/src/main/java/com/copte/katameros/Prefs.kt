package com.copte.katameros

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat

/** Réglages de l'application (repris de PreferencesManager de LivreAGPIA). */
class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var fontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = prefs.edit { putFloat(KEY_FONT_SIZE, value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)) }

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, THEME_SYSTEM)
        set(value) = prefs.edit { putInt(KEY_THEME, value) }

    /** Langue principale du contenu, qui pilote aussi l'interface : fr, ar ou en. */
    var primaryLanguage: String
        get() = prefs.getString(KEY_PRIMARY, null)?.takeIf { it in PRIMARY_LANGUAGES } ?: LANG_FR
        set(value) = prefs.edit {
            putString(KEY_PRIMARY, value)
            if (prefs.getString(KEY_SECONDARY, null) == value) remove(KEY_SECONDARY)
        }

    /**
     * Langue des lectures choisie avec le bouton globe (fr, ar, en, cop), ou `null` pour la langue principale.
     * Mémorisée d'un lancement à l'autre.
     */
    var displayLanguage: String?
        get() = prefs.getString(KEY_SECONDARY, null)?.takeIf { it in ALL_LANGUAGES && it != primaryLanguage }
        set(value) = prefs.edit {
            if (value.isNullOrBlank() || value == primaryLanguage) remove(KEY_SECONDARY) else putString(KEY_SECONDARY, value)
        }

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, value) }

    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** Aligne la langue de l'interface sur la langue principale. Renvoie vrai si elle a changé (activités recréées). */
    fun applyAppLocale(): Boolean {
        val tag = primaryLanguage
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag) return false
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        return true
    }

    companion object {
        private const val NAME = "katameros_prefs"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PRIMARY = "primary_language"
        private const val KEY_SECONDARY = "secondary_language"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"

        const val DEFAULT_FONT_SIZE = 18f
        const val MIN_FONT_SIZE = 12f
        const val MAX_FONT_SIZE = 28f

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val LANG_FR = "fr"
        const val LANG_AR = "ar"
        const val LANG_EN = "en"
        const val LANG_COP = "cop"
        val PRIMARY_LANGUAGES = listOf(LANG_FR, LANG_AR, LANG_EN)
        val ALL_LANGUAGES = listOf(LANG_FR, LANG_AR, LANG_EN, LANG_COP)
    }
}
