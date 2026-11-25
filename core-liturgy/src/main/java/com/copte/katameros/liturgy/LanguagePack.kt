package com.copte.katameros.liturgy

import org.json.JSONObject

/** Métadonnées d'un type de lecture (titre, introduction, conclusion), depuis `texts.json` > `readings`. */
data class ReadingMeta(val title: String?, val introduction: String?, val conclusion: String?)

/** Contenu de `<lang>/texts.json`. */
class Texts(
    val books: Map<Int, String>,
    /** Titres des fêtes, par nom katameros (« Christmas », « PalmSunday »…). */
    val feasts: Map<String, String>,
    val readings: Map<Int, ReadingMeta>,
    val sections: Map<Int, String>,
    val subSectionTitles: Map<Int, String>,
    val subSectionIntroductions: Map<Int, String>,
) {
    companion object {
        val EMPTY = Texts(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())

        fun parse(json: String): Texts {
            val o = JSONObject(json)
            fun strings(name: String): Map<String, String> {
                val m = o.optJSONObject(name) ?: return emptyMap()
                return m.keys().asSequence().mapNotNull { k -> m.optStringOrNull(k)?.let { k to it } }.toMap()
            }
            val readings = o.optJSONObject("readings")?.let { r ->
                r.keys().asSequence().associate { k ->
                    val m = r.getJSONObject(k)
                    k.toInt() to ReadingMeta(m.optStringOrNull("title"), m.optStringOrNull("introduction"), m.optStringOrNull("conclusion"))
                }
            } ?: emptyMap()
            val subs = o.optJSONObject("subSections")
            val subTitles = mutableMapOf<Int, String>()
            val subIntros = mutableMapOf<Int, String>()
            subs?.keys()?.forEach { k ->
                val m = subs.getJSONObject(k)
                m.optStringOrNull("title")?.let { subTitles[k.toInt()] = it }
                m.optStringOrNull("introduction")?.let { subIntros[k.toInt()] = it }
            }
            return Texts(
                books = strings("books").mapKeys { it.key.toInt() },
                feasts = strings("feasts"),
                readings = readings,
                sections = strings("sections").mapKeys { it.key.toInt() },
                subSectionTitles = subTitles,
                subSectionIntroductions = subIntros,
            )
        }
    }
}

/**
 * Bible d'une langue : uniquement les versets cités par le lectionnaire, un fichier par livre
 * (`<lang>/bible/<livre>.json`), lu à la première demande. [loadBook] renvoie le JSON du livre ou `null`.
 */
class Bible(private val loadBook: (Int) -> String?) {
    private val books = HashMap<Int, Map<Int, Map<Int, String>>>()

    @Synchronized
    private fun book(id: Int): Map<Int, Map<Int, String>> =
        books.getOrPut(id) { loadBook(id)?.let(::parseBook) ?: emptyMap() }

    fun verse(book: Int, chapter: Int, verse: Int): String? = book(book)[chapter]?.get(verse)
    fun lastVerse(book: Int, chapter: Int): Int = book(book)[chapter]?.keys?.maxOrNull() ?: 0

    companion object {
        val EMPTY = Bible { null }

        /** `{"chapitre": {"verset": "texte"}}` */
        fun parseBook(json: String): Map<Int, Map<Int, String>> {
            val chapters = JSONObject(json)
            val result = HashMap<Int, Map<Int, String>>()
            for (c in chapters.keys()) {
                val verses = chapters.getJSONObject(c)
                val vMap = HashMap<Int, String>(verses.length())
                for (v in verses.keys()) vMap[v.toInt()] = verses.getString(v)
                result[c.toInt()] = vMap
            }
            return result
        }
    }
}

/** Une commémoration du Synaxaire. [id] est commun à toutes les langues. */
data class Commemoration(val id: String?, val title: String, val html: String)

/** Synaxaire d'une langue, un fichier par mois copte (`<lang>/synaxarium/MM.json`), lu à la demande. */
class Synaxarium(private val loadMonth: (Int) -> String?) {
    private val months = HashMap<Int, Map<String, List<Commemoration>>>()

    /** Commémorations du jour copte de clé « MM-JJ ». */
    @Synchronized
    fun day(key: String): List<Commemoration> {
        val month = key.substringBefore('-').toInt()
        return months.getOrPut(month) { loadMonth(month)?.let(::parse) ?: emptyMap() }[key].orEmpty()
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun parse(json: String): Map<String, List<Commemoration>> {
            val root = JSONObject(json)
            return root.keys().asSequence().associateWith { key ->
                val arr = root.getJSONArray(key)
                (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    // Certains titres contiennent des retours à la ligne : un titre tient sur une ligne logique.
                    val title = c.optString("title").replace(WHITESPACE, " ").trim()
                    Commemoration(c.optStringOrNull("id"), title, c.optString("html"))
                }
            }
        }
    }
}

/** Toutes les données d'une langue. [synaxarium] vaut `null` si la langue n'en a pas (copte). */
class LanguagePack(
    val code: String,
    val rtl: Boolean,
    val texts: Texts,
    val bible: Bible,
    val synaxarium: Synaxarium?,
)

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) getString(name).takeIf { it.isNotBlank() } else null
