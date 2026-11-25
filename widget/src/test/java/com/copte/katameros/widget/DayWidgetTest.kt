package com.copte.katameros.widget

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayWidgetTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val cairo = ZoneId.of("Africa/Cairo")

    private fun at(text: String, zone: ZoneId) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone)

    @Test
    fun `mise a jour juste apres minuit`() {
        assertEquals(at("2026-11-26T00:00:30", paris), DayWidget.nextRefresh(at("2026-11-25T23:59:59", paris)))
        assertEquals(at("2026-11-26T00:00:30", paris), DayWidget.nextRefresh(at("2026-11-25T08:00:00", paris)))
        // Déjà mis à jour à 00:00:30 : la suivante est le lendemain.
        assertEquals(at("2026-11-27T00:00:30", paris), DayWidget.nextRefresh(at("2026-11-26T00:00:30", paris)))
    }

    @Test
    fun `changement d'heure`() {
        // Passage à l'heure d'été en France le 29 mars 2026 à 2 h : minuit existe toujours.
        assertEquals(at("2026-03-29T00:00:30", paris), DayWidget.nextRefresh(at("2026-03-28T22:00:00", paris)))
        // L'Égypte change d'heure à minuit (fin avril) : atStartOfDay prend la première heure valide du jour.
        val next = DayWidget.nextRefresh(at("2026-04-23T23:00:00", cairo))
        assertEquals(24, next.dayOfMonth)
    }
}
