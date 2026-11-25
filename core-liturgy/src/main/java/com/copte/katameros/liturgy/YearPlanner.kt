package com.copte.katameros.liturgy

import com.copte.calendar.Season
import java.time.LocalDate

/** Événement du planning d'une année. */
sealed interface PlannedEvent {
    val date: LocalDate

    /** Fête ou jour propre, [key] = clé du titre dans `texts.json` > `feasts` (« Christmas », « PalmSunday »…). */
    data class Feast(override val date: LocalDate, val key: String, val lastDate: LocalDate) : PlannedEvent

    /** Début d'un temps ou d'un jeûne, jusqu'à [lastDate] inclus. */
    data class SeasonStart(override val date: LocalDate, val season: Season, val lastDate: LocalDate) : PlannedEvent
}

/**
 * Fêtes et temps liturgiques d'une année grégorienne, dans l'ordre : ce que montre le calendrier de l'application.
 * Une fête qui dure plusieurs jours de suite (Croix, paramoun, Ninive) n'apparaît qu'une fois, avec sa date de fin.
 */
object YearPlanner {

    fun events(year: Int, selector: DaySelector): List<PlannedEvent> {
        val events = mutableListOf<PlannedEvent>()
        var openFeast: PlannedEvent.Feast? = null
        var openSeason: PlannedEvent.SeasonStart? = null
        // On commence la veille du 1er janvier pour savoir si un temps (jeûne de la Nativité) est déjà en cours.
        var previousSeason = selector.select(LocalDate.of(year - 1, 12, 31)).day.season
        var date = LocalDate.of(year, 1, 1)
        while (date.year == year) {
            val readings = selector.select(date)
            val key = readings.titleKey
            when {
                key == null -> openFeast = null
                openFeast != null && openFeast.key == key && openFeast.lastDate == date.minusDays(1) -> {
                    val extended = openFeast.copy(lastDate = date)
                    events[events.lastIndexOf(openFeast)] = extended
                    openFeast = extended
                }
                else -> {
                    openFeast = PlannedEvent.Feast(date, key, date).also { events += it }
                }
            }
            val season = readings.day.season
            if (season != previousSeason || date.dayOfYear == 1) {
                openSeason = if (season == Season.ORDINARY) null
                else PlannedEvent.SeasonStart(date, season, date).also { events += it }
            } else if (openSeason != null) {
                val extended = openSeason.copy(lastDate = date)
                events[events.lastIndexOf(openSeason)] = extended
                openSeason = extended
            }
            previousSeason = season
            date = date.plusDays(1)
        }
        // Un temps encore en cours au 31 décembre (jeûne de la Nativité) : sa vraie fin, l'année suivante.
        openSeason?.let { open ->
            var end = open.lastDate
            while (selector.select(end.plusDays(1)).day.season == open.season) end = end.plusDays(1)
            events[events.lastIndexOf(open)] = open.copy(lastDate = end)
        }
        // Un temps qui commence le jour d'une fête s'affiche avant elle.
        return events.sortedWith(compareBy<PlannedEvent> { it.date }.thenBy { if (it is PlannedEvent.SeasonStart) 0 else 1 })
    }
}
