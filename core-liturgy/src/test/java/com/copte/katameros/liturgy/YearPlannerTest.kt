package com.copte.katameros.liturgy

import com.copte.calendar.Season
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YearPlannerTest {
    private val selector = DaySelector(Lectionary.parse(File("../app/src/main/assets/katameros/lectionary.json").readText()))
    private val events = YearPlanner.events(2026, selector)

    private fun feast(key: String) = events.filterIsInstance<PlannedEvent.Feast>().filter { it.key == key }
    private fun season(s: Season) = events.filterIsInstance<PlannedEvent.SeasonStart>().filter { it.season == s }

    @Test
    fun `fetes de 2026`() {
        assertEquals(LocalDate.of(2026, 1, 7), feast("Christmas").single().date)
        assertEquals(LocalDate.of(2026, 4, 12), feast("Easter").single().date)
        assertEquals(LocalDate.of(2026, 4, 5), feast("PalmSunday").single().date)
        // La Croix (17-19 Thout) : un seul événement de 3 jours.
        val cross = feast("Cross").first { it.date.monthValue == 9 }
        assertEquals(LocalDate.of(2026, 9, 27), cross.date)
        assertEquals(LocalDate.of(2026, 9, 29), cross.lastDate)
    }

    @Test
    fun `temps et jeunes de 2026`() {
        // Jeûne de la Nativité déjà en cours au 1er janvier, jusqu'au 6 janvier.
        val nativityFasts = season(Season.NATIVITY_FAST)
        assertEquals(LocalDate.of(2026, 1, 1), nativityFasts.first().date)
        assertEquals(LocalDate.of(2026, 1, 6), nativityFasts.first().lastDate)
        assertEquals(LocalDate.of(2026, 11, 25), nativityFasts.last().date)
        // Fin réelle, l'année suivante (veille de la Nativité du 7 janvier 2027).
        assertEquals(LocalDate.of(2027, 1, 6), nativityFasts.last().lastDate)
        val lent = season(Season.GREAT_LENT).single()
        assertEquals(LocalDate.of(2026, 2, 16), lent.date)
        assertEquals(LocalDate.of(2026, 4, 4), lent.lastDate)
        assertEquals(LocalDate.of(2026, 6, 1), season(Season.APOSTLES_FAST).single().date)
    }

    @Test
    fun `ordre chronologique`() {
        assertTrue(events.zipWithNext().all { (a, b) -> !b.date.isBefore(a.date) })
    }
}
