package com.copte.katameros.liturgy

import com.copte.calendar.CopticDate

/** Verset prêt à afficher. */
data class Verse(val chapter: Int, val number: Int, val text: String)

/** Texte d'une lecture dans une langue. */
data class ReadingText(
    val introduction: String?,
    /** Référence lisible, ex. « Jean 12:1-11 ». */
    val reference: String,
    val verses: List<Verse>,
    val conclusion: String?,
)

/** Éléments à afficher, dans l'ordre. [section] = index de l'office, pour replier ou déplier. */
sealed interface Block {
    val section: Int

    data class OfficeHeader(override val section: Int, val sectionId: Int, val title: String) : Block
    data class SubHeader(override val section: Int, val title: String?, val introduction: String?) : Block
    data class ReadingBlock(
        override val section: Int,
        val type: Int,
        val primary: ReadingText,
        /** Même lecture dans la langue secondaire, ou `null`. */
        val secondary: ReadingText?,
    ) : Block
    data class CommemorationBlock(override val section: Int, val commemoration: Commemoration) : Block
}

/**
 * Met en forme le programme d'un jour dans une langue (et éventuellement une seconde),
 * comme `ReadingsRepository` de katameros-api : introductions avec le nom du livre (« $ »),
 * numéro de l'épître et formules propres à chaque lecture.
 *
 * @param labels libellés d'interface absents des données (veille, mois coptes pour le Synaxaire).
 */
class DayRenderer(
    private val primary: LanguagePack,
    private val secondary: LanguagePack?,
    private val labels: Labels,
) {
    /** Libellés fournis par l'application (ressources Android). */
    interface Labels {
        /** Titre d'une heure de la veille (sections 9 à 13), à partir du titre de l'heure correspondante. */
        fun eveHour(hourTitle: String): String
        fun copticMonth(month: Int): String
        fun ordinal(day: Int): String
    }

    fun render(readings: DayReadings): List<Block> {
        val blocks = mutableListOf<Block>()
        readings.programme.sections.forEachIndexed { index, section ->
            blocks += Block.OfficeHeader(index, section.id, sectionTitle(section.id))
            for (sub in section.subSections) {
                if (sub.synaxarium) {
                    renderSynaxarium(index, readings.day.coptic, blocks)
                    continue
                }
                if (sub.readings.isEmpty()) continue
                blocks += subHeader(index, sub)
                val hasPsalm = sub.readings.any { it.type == Reading.PSALM }
                for (reading in sub.readings) {
                    blocks += Block.ReadingBlock(
                        section = index,
                        type = reading.type,
                        primary = text(primary, reading, hasPsalm),
                        secondary = secondary?.let { text(it, reading, hasPsalm) },
                    )
                }
            }
        }
        return blocks
    }

    private fun sectionTitle(id: Int): String {
        val t = primary.texts.sections
        return when (id) {
            in 9..13 -> labels.eveHour(t[id - 5] ?: "")
            else -> t[id] ?: ""
        }
    }

    private fun subHeader(index: Int, sub: SubSection): Block.SubHeader {
        val texts = primary.texts
        val title = when (sub.id) {
            SubSection.PAULINE, SubSection.CATHOLIC, SubSection.ACTS -> texts.readings[sub.id]?.title
            else -> texts.subSectionTitles[sub.id]
        }
        val gospel = sub.readings.firstOrNull { it.type == Reading.GOSPEL }
        val intro = if (sub.introduction && sub.id == SubSection.PSALM_AND_GOSPEL && gospel != null) {
            texts.subSectionIntroductions[sub.id]?.replace("$", bookLetters(primary, gospel.ref))
        } else null
        return Block.SubHeader(index, title, intro)
    }

    private fun renderSynaxarium(index: Int, date: CopticDate, blocks: MutableList<Block>) {
        val entries = primary.synaxarium?.day(date.key).orEmpty()
        if (entries.isEmpty()) return
        val texts = primary.texts
        val intro = texts.subSectionIntroductions[SubSection.SYNAXARIUM]?.let { synaxariumIntro(it, date) }
        blocks += Block.SubHeader(index, texts.readings[6]?.title, intro)
        entries.forEach { blocks += Block.CommemorationBlock(index, it) }
    }

    private fun synaxariumIntro(template: String, date: CopticDate): String {
        var s = template.replaceFirst("$", labels.ordinal(date.day)).replaceFirst("$", labels.copticMonth(date.month.number))
        val options = BRACKETS.findAll(s).map { it.groupValues[1] }.toList()
        if (options.size >= 2) {
            val first = s.indexOf('[')
            val last = s.lastIndexOf(']')
            s = s.substring(0, first) + (if (date.day <= 15) options[0] else options[1]) + s.substring(last + 1)
        }
        return s.trim()
    }

    private fun text(pack: LanguagePack, reading: Reading, hasPsalm: Boolean): ReadingText {
        val passages = ReadingRef.passages(reading.ref)
        val verses = passages.flatMap { p ->
            p.verses(pack.bible::lastVerse).mapNotNull { (c, v) -> pack.bible.verse(p.book, c, v)?.let { Verse(c, v, it) } }
        }
        // Chapitre:versets isolés en gauche-à-droite (U+2066…U+2069) : sinon, en arabe, « 24:42-47 » s'affiche « 47-24:42 ».
        // Nom du livre absent dans une langue (copte) : celui de la langue principale.
        val reference = passages.joinToString(" ; ") {
            "${pack.texts.books[it.book] ?: primary.texts.books[it.book] ?: it.book} ⁦${it.chapter}:${it.spec}⁩"
        }
        val meta = pack.texts.readings[reading.type]
        if (reading.simple || meta == null) return ReadingText(null, reference, verses, null)

        val first = passages.first()
        val name = bookLetters(pack, reading.ref)
        val intro = meta.introduction?.let { template ->
            when (reading.type) {
                Reading.PAULINE -> pauline(template.replace("$", name), first.book)
                Reading.CATHOLIC -> numerise(template.replace("$", name), first.book, lastIndex = -1)
                Reading.GOSPEL, GOSPEL_LENT -> if (!hasPsalm) null else template.replace("$", name)
                else -> template.replace("$", name)
            }
        }
        return ReadingText(intro?.let(::tidy), reference, verses, meta.conclusion)
    }

    /** Épîtres de Paul : numéro de l'épître, puis « à » ou « aux » (singulier pour Tite, Philémon, Timothée). */
    private fun pauline(template: String, book: Int): String {
        var s = numerise(template, book, lastIndex = template.indexOf("] "))
        val first = s.indexOf('[')
        val last = s.lastIndexOf(']')
        if (first >= 0 && last > first) {
            val options = BRACKETS.findAll(s).map { it.groupValues[1] }.toList()
            if (options.size >= 2) {
                val noun = if (book in SINGULAR_RECIPIENT) options[0] else options[1]
                s = s.substring(0, first) + noun + s.substring(last + 1)
            }
        }
        return s
    }

    /** Remplace « [Première][Deuxième][Troisième] » par le bon rang (ou rien) selon le livre. */
    private fun numerise(template: String, book: Int, lastIndex: Int): String {
        val first = template.indexOf('[')
        val last = if (lastIndex != -1) lastIndex else template.lastIndexOf(']')
        if (first == -1 || last == -1 || last < first) return template
        val options = BRACKETS.findAll(template.substring(first, last + 1)).map { it.groupValues[1] }.toList()
        val number = when (book) {
            in FIRST_EPISTLES -> options.getOrNull(0)
            in SECOND_EPISTLES -> options.getOrNull(1)
            THIRD_JOHN -> options.getOrNull(2)
            else -> null
        } ?: ""
        return template.substring(0, first) + number + template.substring(last + 1)
    }

    /** Nom du premier livre cité, en lettres seulement (« 1 Corinthiens » → « Corinthiens »), comme le C#. */
    private fun bookLetters(pack: LanguagePack, ref: String): String {
        val book = ReadingRef.passages(ref).firstOrNull()?.book ?: return ""
        return pack.texts.books[book]?.filter { it.isLetter() } ?: ""
    }

    private fun tidy(s: String) = s.replace(Regex(" {2,}"), " ").trim()

    private companion object {
        const val GOSPEL_LENT = 8
        val BRACKETS = Regex("\\[(.*?)]")
        val FIRST_EPISTLES = setOf(46, 52, 54, 60, 62)
        val SECOND_EPISTLES = setOf(47, 53, 55, 61, 63)
        const val THIRD_JOHN = 64
        // 1 et 2 Timothée, Tite, Philémon (le C# d'origine oubliait 1 Timothée : « aux Timothée »).
        val SINGULAR_RECIPIENT = setOf(54, 55, 56, 57)
    }
}
