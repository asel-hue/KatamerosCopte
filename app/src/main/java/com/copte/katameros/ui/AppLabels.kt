package com.copte.katameros.ui

import android.content.Context
import com.copte.calendar.Season
import com.copte.katameros.R
import com.copte.katameros.liturgy.DayRenderer

/** Libellés d'interface utilisés par le rendu des lectures et par l'en-tête du jour. */
class AppLabels(private val context: Context, private val language: String) : DayRenderer.Labels {

    private val months = context.resources.getStringArray(R.array.coptic_months)

    override fun eveHour(hourTitle: String) = context.getString(R.string.eve_hour, hourTitle)

    override fun copticMonth(month: Int): String = months[month - 1]

    override fun ordinal(day: Int): String = when (language) {
        "fr" -> if (day == 1) "1er" else "${day}e"
        "en" -> day.toString() + when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        else -> day.toString()
    }

    /** Nom du temps liturgique, ou `null` pour le temps ordinaire. */
    fun season(season: Season): String? = when (season) {
        Season.ORDINARY -> null
        Season.NATIVITY_FAST -> context.getString(R.string.season_nativity_fast)
        Season.NINEVEH_FAST -> context.getString(R.string.season_nineveh_fast)
        Season.GREAT_LENT -> context.getString(R.string.season_great_lent)
        Season.HOLY_WEEK -> context.getString(R.string.season_holy_week)
        Season.HOLY_FIFTY_DAYS -> context.getString(R.string.season_holy_fifty)
        Season.APOSTLES_FAST -> context.getString(R.string.season_apostles_fast)
        Season.VIRGIN_FAST -> context.getString(R.string.season_virgin_fast)
    }
}
