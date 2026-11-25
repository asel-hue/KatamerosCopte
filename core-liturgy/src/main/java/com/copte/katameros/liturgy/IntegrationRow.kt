package com.copte.katameros.liturgy

import com.copte.calendar.LiturgyContract
import com.copte.calendar.LiturgyContract.Columns
import org.json.JSONArray
import org.json.JSONObject

/** Ligne du ContentProvider pour un jour (contrat [LiturgyContract], colonnes [Columns.ALL]). */
object IntegrationRow {

    /**
     * @param pack données de la langue demandée (titres, Synaxaire).
     * @param seasonTitle titre du temps liturgique dans cette langue, utilisé quand il n'y a pas de fête.
     */
    fun build(readings: DayReadings, pack: LanguagePack, seasonTitle: String?): Map<String, Any?> {
        val day = readings.day
        val c = day.coptic
        val title = readings.titleKey?.let { pack.texts.feasts[it] } ?: seasonTitle
        val commemorations = JSONArray()
        pack.synaxarium?.day(c.key).orEmpty().forEach {
            commemorations.put(JSONObject().put("id", it.id ?: JSONObject.NULL).put("title", it.title.trim()))
        }
        return linkedMapOf(
            Columns.CONTRACT_VERSION to LiturgyContract.VERSION,
            Columns.DATE to day.date.toString(),
            Columns.COPTIC_YEAR to c.year,
            Columns.COPTIC_MONTH to c.month.number,
            Columns.COPTIC_DAY to c.day,
            Columns.COPTIC_KEY to c.key,
            Columns.SEASON_ID to day.season.id,
            Columns.FEAST_IDS to JSONArray(day.feasts.map { it.id }).toString(),
            Columns.TITLE to (title ?: ""),
            Columns.LANG to pack.code,
            Columns.COMMEMORATIONS to commemorations.toString(),
            Columns.READINGS_SOURCE to readings.source,
            Columns.DAY_LINK to LiturgyContract.dayLink(day.date),
        )
    }
}
