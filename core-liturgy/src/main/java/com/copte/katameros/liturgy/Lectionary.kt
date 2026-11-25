package com.copte.katameros.liturgy

import org.json.JSONArray
import org.json.JSONObject

/**
 * Programme d'un jour : offices (sections) → rubriques (sous-sections) → lectures.
 * Les identifiants reprennent ceux de katameros-api et servent de clés vers `<lang>/texts.json`.
 */
data class Programme(val sections: List<Section>)

data class Section(val id: Int, val subSections: List<SubSection>) {
    companion object {
        const val VESPERS = 1
        const val MATINS = 2
        const val LITURGY = 3
        // 4..8 : heures du jour (1re, 3e, 6e, 9e, 11e) ; 9..13 : heures de la veille ; 14 : 12e heure.
    }
}

/** Rubrique. [synaxarium] : emplacement du Synaxaire du jour, rempli à l'affichage. */
data class SubSection(
    val id: Int,
    val readings: List<Reading> = emptyList(),
    val synaxarium: Boolean = false,
    val introduction: Boolean = false,
) {
    companion object {
        const val PSALM_AND_GOSPEL = 1
        const val PROPHECY = 2
        const val PAULINE = 3
        const val CATHOLIC = 4
        const val ACTS = 5
        const val SYNAXARIUM = 10
    }
}

/** Lecture : [type] (1 Psaume, 2 Évangile, 3 Paul, 4 Catholicon, 5 Praxis, 7 Prophétie), [ref] (grammaire ReadingRef). */
data class Reading(val type: Int, val ref: String, val simple: Boolean = false) {
    companion object {
        const val PSALM = 1
        const val GOSPEL = 2
        const val PAULINE = 3
        const val CATHOLIC = 4
        const val ACTS = 5
        const val PROPHECY = 7
    }
}

/** Contenu de `assets/katameros/lectionary.json`. */
class Lectionary(
    val annual: Map<String, Programme>,
    val sundays: Map<String, Programme>,
    val greatLent: Map<String, Programme>,
    val pentecost: Map<String, Programme>,
    val feasts: Map<String, Programme>,
) {
    companion object {
        fun parse(json: String): Lectionary {
            val root = JSONObject(json)
            return Lectionary(
                annual = root.tableOf("annual"),
                sundays = root.tableOf("sundays"),
                greatLent = root.tableOf("greatLent"),
                pentecost = root.tableOf("pentecost"),
                feasts = root.optJSONObject("feasts")?.let { f ->
                    f.keys().asSequence().associateWith { parseProgramme(f.getJSONObject(it)) }
                } ?: emptyMap(),
            )
        }

        private fun JSONObject.tableOf(name: String): Map<String, Programme> {
            val table = optJSONObject(name) ?: return emptyMap()
            return table.keys().asSequence().associateWith { fromOffices(table.getJSONObject(it)) }
        }

        /**
         * Ligne de table (vespers / matins / liturgy) → programme, comme `ReadingsRepository.GetFromRef` :
         * veille seulement si un psaume est donné, prophéties au matin, Synaxaire après les Praxis.
         */
        private fun fromOffices(o: JSONObject): Programme {
            val sections = mutableListOf<Section>()
            o.optJSONObject("vespers")?.let { v ->
                v.optRef("psalm")?.let { psalm ->
                    sections += Section(Section.VESPERS, listOf(psalmAndGospel(psalm, v.optRef("gospel"))))
                }
            }
            o.optJSONObject("matins")?.let { m ->
                val subs = mutableListOf<SubSection>()
                m.optRef("prophecy")?.let { p ->
                    subs += SubSection(SubSection.PROPHECY, ReadingRef.splitReadings(p).map { Reading(Reading.PROPHECY, it) })
                }
                subs += psalmAndGospel(m.optRef("psalm"), m.optRef("gospel"))
                sections += Section(Section.MATINS, subs)
            }
            o.optJSONObject("liturgy")?.let { l ->
                val subs = mutableListOf<SubSection>()
                l.optRef("pauline")?.let { subs += SubSection(SubSection.PAULINE, listOf(Reading(Reading.PAULINE, it))) }
                l.optRef("catholic")?.let { subs += SubSection(SubSection.CATHOLIC, listOf(Reading(Reading.CATHOLIC, it))) }
                l.optRef("acts")?.let { subs += SubSection(SubSection.ACTS, listOf(Reading(Reading.ACTS, it))) }
                subs += SubSection(SubSection.SYNAXARIUM, synaxarium = true)
                if (l.optRef("gospel") != null) subs += psalmAndGospel(l.optRef("psalm"), l.optRef("gospel"))
                sections += Section(Section.LITURGY, subs)
            }
            return Programme(sections)
        }

        private fun psalmAndGospel(psalm: String?, gospel: String?) = SubSection(
            id = SubSection.PSALM_AND_GOSPEL,
            readings = listOfNotNull(psalm?.let { Reading(Reading.PSALM, it) }, gospel?.let { Reading(Reading.GOSPEL, it) }),
            introduction = true,
        )

        private fun JSONObject.optRef(name: String): String? =
            if (has(name) && !isNull(name)) getString(name).takeIf { it.isNotBlank() } else null

        private fun parseProgramme(o: JSONObject): Programme {
            val sections = o.getJSONArray("sections").objects().map { s ->
                Section(
                    id = s.getInt("id"),
                    subSections = s.getJSONArray("subSections").objects().map { sub ->
                        SubSection(
                            id = sub.getInt("id"),
                            readings = sub.optJSONArray("readings")?.objects()?.map {
                                Reading(it.getInt("type"), it.getString("ref"), it.optBoolean("simple"))
                            } ?: emptyList(),
                            synaxarium = sub.optBoolean("synaxarium"),
                            introduction = sub.optBoolean("introduction"),
                        )
                    },
                )
            }
            return Programme(sections)
        }

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    }
}
