package com.copte.katameros.liturgy

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie le portage du choix des lectures sur des dates dont le résultat est connu
 * (analyse du code de katameros-api, dates représentatives de chaque fête et de chaque cas spécial).
 */
class DaySelectorTest {

    private val lectionary = Lectionary.parse(File("../app/src/main/assets/katameros/lectionary.json").readText())
    private val selector = DaySelector(lectionary)

    private fun source(d: String): String {
        val (dd, mm, yyyy) = d.split('-').map(String::toInt)
        return selector.select(LocalDate.of(yyyy, mm, dd)).source
    }

    @Test
    fun `fetes a lectures propres`() {
        val expected = mapOf(
            "07-01-2026" to "feast:christmas",
            "18-01-2026" to "feast:theophany_paramoun",
            "15-02-2025" to "feast:temple_entrance",
            "15-02-2026" to "feast:temple_entrance", // 8 Amshir l'emporte sur la préparation du Carême
            "14-02-2026" to "feast:lent_preparation_saturday",
            "23-02-2025" to "feast:lent_preparation_sunday",
            "02-02-2026" to "feast:nineveh_1",
            "03-02-2026" to "feast:nineveh_2",
            "04-02-2026" to "feast:nineveh_3",
            "05-02-2026" to "feast:jonah",
            "07-04-2025" to "feast:annunciation",
            "07-04-2026" to "feast:pascha_tuesday", // Annonciation non célébrée en Semaine sainte
            "08-11-2026" to "feast:month_29",
            "04-04-2026" to "feast:lazarus_saturday",
            "05-04-2026" to "feast:palm_sunday",
            "06-04-2026" to "feast:pascha_monday",
            "08-04-2026" to "feast:pascha_wednesday",
            "09-04-2026" to "feast:pascha_thursday",
            "10-04-2026" to "feast:pascha_friday",
            "11-04-2026" to "feast:pascha_saturday",
            "12-04-2026" to "feast:easter",
            "01-06-2026" to "feast:egypt_entrance",
            "27-09-2026" to "feast:cross_1",
            "28-09-2026" to "feast:cross_2",
            "29-09-2026" to "feast:cross_3",
            "19-03-2026" to "feast:cross_1", // 10 Baramhat
        )
        expected.forEach { (date, src) -> assertEquals(date, src, source(date)) }
    }

    @Test
    fun `fetes qui reprennent une ligne annuelle`() {
        assertEquals("annual:01-01", source("11-09-2026"))  // Nayrouz
        assertEquals("annual:05-11", source("19-01-2026"))  // Théophanie
        assertEquals("annual:05-13", source("21-01-2026"))  // Cana
        assertEquals("annual:04-28", source("06-01-2026"))  // paramoun de la Nativité
        assertEquals("annual:04-28", source("04-01-2030"))  // 6 janvier un dimanche : 3 jours
        assertEquals("annual:04-28", source("05-01-2030"))
    }

    @Test
    fun `cas speciaux de Kiahk et de la Theophanie`() {
        assertEquals("annual:04-30", source("08-01-2023"))  // dimanche 30 Kiahk
        assertEquals("annual:04-29", source("08-01-2024"))  // 29 Kiahk répété
        assertEquals("sundays:04-1", source("09-12-2029"))  // dimanches de Kiahk décalés
        assertEquals("sundays:04-2", source("16-12-2029"))
        assertEquals("sundays:04-3", source("23-12-2029"))
        assertEquals("sundays:04-4", source("30-12-2029"))
        assertEquals("feast:theophany_paramoun", source("16-01-2026"))
        assertEquals("feast:theophany_paramoun", source("17-01-2026"))
        assertEquals("feast:theophany_paramoun", source("17-01-2025"))
    }

    @Test
    fun `tables du Careme, de la Pentecote, des dimanches et de l'annee`() {
        assertEquals("greatLent:1-1", source("16-02-2026"))   // lundi P − 55
        assertEquals("greatLent:7-5", source("03-04-2026"))   // vendredi P − 9
        assertEquals("pentecost:1-1", source("13-04-2026"))   // lundi de Pâques + 1
        assertEquals("pentecost:1-0", source("19-04-2026"))   // dimanche de Thomas
        assertEquals("annual:03-16", source("25-11-2026"))    // mercredi 16 Hatour
        assertEquals("sundays:03-3", source("29-11-2026"))    // 3e dimanche de Hatour
        val thomas = selector.select(LocalDate.of(2026, 4, 19))
        assertEquals("ThomasSunday", thomas.titleKey)
        assertNull(selector.select(LocalDate.of(2026, 11, 25)).titleKey)
    }

    @Test
    fun `chaque jour de 2024 a 2040 a des lectures et des references lisibles`() {
        var date = LocalDate.of(2024, 1, 1)
        while (date.year <= 2040) {
            val readings = selector.select(date)
            assertTrue("$date sans office", readings.programme.sections.isNotEmpty())
            readings.programme.sections.flatMap { it.subSections }.flatMap { it.readings }.forEach {
                assertTrue("$date ${it.ref}", ReadingRef.passages(it.ref).isNotEmpty())
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `references a cheval sur deux chapitres`() {
        val p = ReadingRef.passages("49.1:15-2:3").single()
        val verses = p.verses { _, _ -> 23 }
        assertEquals(1 to 15, verses.first())
        assertEquals(2 to 3, verses.last())
        assertEquals(12, verses.size)
        assertEquals(listOf(11, 10), ReadingRef.passages("19.2:11,10").single().verses { _, _ -> 12 }.map { it.second })
        assertEquals(2, ReadingRef.passages("58.11:32-40*@+58.12:1-2").size)
        assertEquals(4, ReadingRef.splitReadings("23.42:5-16@20.4:20-27@18.29:2-20@9.3:1-20").size)
    }
}
