package com.copte.katameros.liturgy

import com.copte.calendar.LiturgyContract.Columns
import java.io.File
import java.time.LocalDate
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class IntegrationRowTest {
    private val assets = File("../app/src/main/assets/katameros")
    private val selector = DaySelector(Lectionary.parse(File(assets, "lectionary.json").readText()))
    private fun pack(code: String) = LanguagePack(
        code, code == "ar",
        Texts.parse(File(assets, "$code/texts.json").readText()),
        Bible { null },
        Synaxarium { m -> File(assets, "$code/synaxarium/%02d.json".format(m)).takeIf { it.exists() }?.readText() },
    )

    @Test
    fun `ligne du 16 Hatour`() {
        val row = IntegrationRow.build(selector.select(LocalDate.of(2026, 11, 25)), pack("fr"), "Jeûne de la Nativité")
        assertEquals(Columns.ALL, row.keys.toList())
        assertEquals(1743, row[Columns.COPTIC_YEAR])
        assertEquals("03-16", row[Columns.COPTIC_KEY])
        assertEquals("nativity_fast", row[Columns.SEASON_ID])
        assertEquals("Jeûne de la Nativité", row[Columns.TITLE])
        val saints = JSONArray(row[Columns.COMMEMORATIONS] as String)
        assertEquals(5, saints.length())
        assertEquals("31601", saints.getJSONObject(0).getString("id"))
        assertEquals("copte://jour/2026-11-25", row[Columns.DAY_LINK])
    }

    @Test
    fun `fete en arabe`() {
        val row = IntegrationRow.build(selector.select(LocalDate.of(2027, 1, 7)), pack("ar"), null)
        assertEquals("[\"nativity\"]", row[Columns.FEAST_IDS])
        assertEquals("ar", row[Columns.LANG])
        assertEquals(true, (row[Columns.TITLE] as String).isNotBlank())
        assertEquals("feast:christmas", row[Columns.READINGS_SOURCE])
    }
}
