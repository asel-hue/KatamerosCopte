package com.copte.katameros.liturgy

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayRendererTest {

    private val assets = File("../app/src/main/assets/katameros")
    private fun pack(code: String) = LanguagePack(
        code = code,
        rtl = code == "ar",
        texts = File(assets, "$code/texts.json").takeIf { it.exists() }?.let { Texts.parse(it.readText()) } ?: Texts.EMPTY,
        bible = Bible { book -> File(assets, "$code/bible/$book.json").takeIf { it.exists() }?.readText() },
        synaxarium = File(assets, "$code/synaxarium").takeIf { it.isDirectory }?.let { dir ->
            Synaxarium { month -> File(dir, "%02d.json".format(month)).takeIf { it.exists() }?.readText() }
        },
    )

    private val fr = pack("fr")
    private val selector = DaySelector(Lectionary.parse(File(assets, "lectionary.json").readText()))
    private val labels = object : DayRenderer.Labels {
        override fun eveHour(hourTitle: String) = "$hourTitle de la veille"
        override fun copticMonth(month: Int) = "Hatour"
        override fun ordinal(day: Int) = if (day == 1) "1er" else "${day}e"
    }

    private fun render(date: LocalDate, secondary: LanguagePack? = null) =
        DayRenderer(fr, secondary, labels).render(selector.select(date))

    @Test
    fun `jour ordinaire 16 Hatour en francais`() {
        val blocks = render(LocalDate.of(2026, 11, 25))
        val offices = blocks.filterIsInstance<Block.OfficeHeader>().map { it.title }
        assertEquals(listOf("Office de la Veille", "Office du Matin", "Messe"), offices)
        val readings = blocks.filterIsInstance<Block.ReadingBlock>()
        assertTrue(readings.all { it.primary.verses.isNotEmpty() })
        readings.mapNotNull { it.primary.introduction }.forEach {
            assertFalse("gabarit non rempli : $it", '$' in it || '[' in it)
        }
        // Hébreux 11:32-40 puis 12:1-2 : « Épître de Saint Paul aux Hébreux »
        val pauline = readings.first { it.type == Reading.PAULINE }
        assertTrue(pauline.primary.introduction!!, pauline.primary.introduction!!.contains("aux Hébreux"))
        assertEquals(11, pauline.primary.verses.size)
        assertEquals(5, blocks.filterIsInstance<Block.CommemorationBlock>().size)
        val synaxariumIntro = blocks.filterIsInstance<Block.SubHeader>().first { it.introduction?.contains("Hatour") == true }
        assertTrue(synaxariumIntro.introduction!!.contains("16e"))
        assertTrue(synaxariumIntro.introduction!!.contains("le quitter"))
    }

    @Test
    fun `epitre numerotee et au singulier`() {
        // 1re épître à Timothée : « Première Épître de Saint Paul à Timothée »
        val blocks = (1..366).asSequence().map { LocalDate.of(2026, 9, 11).plusDays(it.toLong()) }
            .flatMap { render(it).filterIsInstance<Block.ReadingBlock>() }
            .filter { it.type == Reading.PAULINE }
        val timothy = blocks.firstOrNull { it.primary.reference.startsWith(fr.texts.books[54]!!) }
        assertNotNull("aucune lecture de 1 Timothée sur l'année", timothy)
        val intro = timothy!!.primary.introduction!!
        assertTrue(intro, intro.startsWith("Première Épître de Saint Paul à Timothée"))
    }

    @Test
    fun `semaine sainte et bilingue francais arabe`() {
        val ar = pack("ar")
        val blocks = render(LocalDate.of(2026, 4, 10), ar) // Vendredi saint
        val readings = blocks.filterIsInstance<Block.ReadingBlock>()
        assertTrue(readings.size > 20)
        assertTrue(readings.all { it.secondary != null && it.secondary!!.verses.isNotEmpty() })
        assertTrue(blocks.filterIsInstance<Block.OfficeHeader>().size == 6)
    }

    @Test
    fun `copte en seconde langue sans textes ni synaxaire`() {
        val cop = pack("cop")
        val blocks = render(LocalDate.of(2026, 11, 25), cop)
        val gospel = blocks.filterIsInstance<Block.ReadingBlock>().last { it.type == Reading.GOSPEL }
        assertTrue(gospel.secondary!!.verses.isNotEmpty())
    }
}
