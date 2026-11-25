package com.copte.katameros.liturgy

/**
 * Grammaire des références de katameros-api :
 * - « @ » sépare des lectures (prophéties), « *@+ » enchaîne des passages d'une même lecture ;
 * - passage « Livre.Chapitre:Versets », versets « 6,7,9 » (dans l'ordre écrit), « 1-5 », « 26-end »
 *   ou « 26-2:7 » (jusqu'à un chapitre suivant).
 */
data class Passage(val book: Int, val chapter: Int, val spec: String) {

    /** (chapitre, verset) couverts, dans l'ordre de lecture. [lastVerse] donne le dernier verset d'un chapitre. */
    fun verses(lastVerse: (book: Int, chapter: Int) -> Int): List<Pair<Int, Int>> =
        spec.split(',').flatMap { item ->
            val part = item.trim()
            val dash = part.indexOf('-')
            if (dash < 0) return@flatMap listOf(chapter to part.toInt())
            val start = part.substring(0, dash).toInt()
            val end = part.substring(dash + 1)
            when {
                end == "end" -> (start..lastVerse(book, chapter)).map { chapter to it }
                ':' in end -> {
                    val (endCh, endV) = end.split(':').map(String::toInt)
                    (chapter..endCh).flatMap { ch ->
                        val first = if (ch == chapter) start else 1
                        val last = if (ch == endCh) endV else lastVerse(book, ch)
                        (first..last).map { ch to it }
                    }
                }
                else -> (start..end.toInt()).map { chapter to it }
            }
        }
}

object ReadingRef {
    private val READING_SEPARATOR = Regex("(?<!\\*)@(?!\\+)")
    private val PASSAGE = Regex("^(\\d+)\\.(\\d+):(.+)$")

    /** Découpe en lectures distinctes (séparateur « @ »). */
    fun splitReadings(ref: String): List<String> = ref.split(READING_SEPARATOR).map(String::trim).filter(String::isNotEmpty)

    /** Tous les passages d'une référence, quelle que soit la façon dont ils sont séparés. */
    fun passages(ref: String): List<Passage> =
        splitReadings(ref).flatMap { it.split("*@+") }.map(String::trim).filter(String::isNotEmpty).map { part ->
            val m = PASSAGE.matchEntire(part) ?: throw IllegalArgumentException("Référence illisible : $part")
            Passage(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3])
        }
}
