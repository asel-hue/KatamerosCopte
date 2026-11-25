package com.copte.katameros.liturgy

import com.copte.calendar.CopticCalendar
import com.copte.calendar.CopticDate
import com.copte.calendar.CopticMonth
import com.copte.calendar.LiturgicalCalendar
import com.copte.calendar.LiturgicalDay
import com.copte.calendar.Paschalion
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Lectures choisies pour un jour.
 * [source] : origine stable, par exemple « annual:03-16 », « sundays:03-3 », « greatLent:1-1 », « feast:palm_sunday ».
 * [titleKey] : clé du titre dans `texts.json` > `feasts` (nom katameros, ex. « Christmas »), ou `null`.
 */
data class DayReadings(
    val day: LiturgicalDay,
    val source: String,
    val titleKey: String?,
    val programme: Programme,
)

/**
 * Choix des lectures d'une date : portage de `LectionaryRepository.GetForDay` (katameros-api, MIT).
 * Ordre : cas spéciaux (Kiahk, Théophanie) → fête avec lectures propres → Carême → Pentecôte →
 * dimanche du 29 → dimanche du mois → jour annuel.
 */
class DaySelector(private val lectionary: Lectionary) {

    fun select(date: LocalDate): DayReadings {
        val day = LiturgicalCalendar.dayOf(date)
        val c = day.coptic
        val p = day.daysFromEaster

        specialCase(date, c)?.let { (source, title, programme) -> return DayReadings(day, source, title, programme) }

        val feast = FEASTS.firstOrNull { it.matches(date, c, p) }
        if (feast?.programme != null) {
            val (source, programme) = feast.programme.invoke(this, c)
            return DayReadings(day, source, feast.titleKey, programme)
        }
        val title = feast?.titleKey

        val lentStart = Paschalion.easterFor(date).minusDays(55)
        val lentEnd = Paschalion.easterFor(date).minusDays(9)
        val dow = date.dayOfWeek.value % 7 // 0 = dimanche, comme .NET
        return when {
            !date.isBefore(lentStart) && !date.isAfter(lentEnd) -> {
                val week = ((date.toEpochDay() - lentStart.toEpochDay()) / 7 + 1).toInt()
                table("greatLent", "$week-$dow", day, title)
            }
            p in 1..49 -> {
                val week = p / 7 + 1 - (if (date.dayOfWeek == DayOfWeek.SUNDAY) 1 else 0)
                table("pentecost", "$week-$dow", day, title)
            }
            c.day == 29 && date.dayOfWeek == DayOfWeek.SUNDAY &&
                c.month != CopticMonth.AMSHIR && c.month != CopticMonth.TOUBA ->
                DayReadings(day, "feast:month_29", null, feastProgramme("month_29"))
            date.dayOfWeek == DayOfWeek.SUNDAY -> {
                val n = (c.day - 1) / 7 + 1
                val key = "%02d-%d".format(java.util.Locale.ROOT, c.month.number, n)
                if (key in lectionary.sundays) table("sundays", key, day, title) else annual(c.key, day, title)
            }
            else -> annual(c.key, day, title)
        }
    }

    private fun annual(key: String, day: LiturgicalDay, title: String?) = table("annual", key, day, title)

    private fun table(name: String, key: String, day: LiturgicalDay, title: String?): DayReadings {
        val t = when (name) {
            "annual" -> lectionary.annual
            "sundays" -> lectionary.sundays
            "greatLent" -> lectionary.greatLent
            else -> lectionary.pentecost
        }
        val programme = t[key] ?: error("Lectures introuvables : $name:$key (${day.date})")
        return DayReadings(day, "$name:$key", title, programme)
    }

    private fun feastProgramme(key: String): Programme =
        lectionary.feasts[key] ?: error("Fête absente de lectionary.json : $key")

    // ---- Cas spéciaux (Factories/SpecialCases) ----

    private fun specialCase(date: LocalDate, c: CopticDate): Triple<String, String?, Programme>? {
        kiahk(date, c)?.let { return it }
        return theophany(date, c)
    }

    private fun kiahk(date: LocalDate, c: CopticDate): Triple<String, String?, Programme>? {
        val sunday = date.dayOfWeek == DayOfWeek.SUNDAY
        if (c.month == CopticMonth.KIAHK && c.day == 30 && sunday) {
            return Triple("annual:04-30", null, lectionary.annual.getValue("04-30"))
        }
        if (c.month == CopticMonth.KIAHK && c.day == 29) {
            val jan7 = CopticCalendar.fromGregorian(LocalDate.of(date.year, 1, 7))
            if (jan7.month == CopticMonth.KIAHK && jan7.day == 28) {
                return Triple("annual:04-29", null, lectionary.annual.getValue("04-29"))
            }
        }
        // Quand le 6 janvier suivant est un dimanche, les dimanches de Kiahk sont décalés d'un rang.
        if (sunday && date.monthValue != 1 && LocalDate.of(date.year + 1, 1, 6).dayOfWeek == DayOfWeek.SUNDAY) {
            val key = when {
                c.month == CopticMonth.HATOUR && c.day + 7 > 30 -> "04-1"
                c.month == CopticMonth.KIAHK -> "04-${(c.day - 1) / 7 + 2}"
                else -> null
            }
            lectionary.sundays[key]?.let { return Triple("sundays:$key", null, it) }
        }
        return null
    }

    private fun theophany(date: LocalDate, c: CopticDate): Triple<String, String?, Programme>? {
        if (c.month != CopticMonth.TOUBA) return null
        val feastDay = CopticCalendar.toGregorian(CopticDate(c.year, CopticMonth.TOUBA, 11)).dayOfWeek
        val applies = (feastDay == DayOfWeek.MONDAY && c.day in 8..9) || (feastDay == DayOfWeek.SUNDAY && c.day == 9)
        return if (applies) Triple("feast:theophany_paramoun", "TheophanyParamoun", feastProgramme("theophany_paramoun")) else null
    }

    // ---- Fêtes (Factories/FeastsFactory, dans le même ordre : la première qui correspond gagne) ----

    private class FeastRule(
        val titleKey: String?,
        val easterOffset: Int? = null,
        val coptic: Pair<CopticMonth, Int>? = null,
        val gregorian: Pair<Int, Int>? = null,
        val validator: ((LocalDate, CopticDate) -> Boolean)? = null,
        /** Lectures propres, ou `null` si la fête ne change que le titre. */
        val programme: (DaySelector.(CopticDate) -> Pair<String, Programme>)? = null,
    ) {
        fun matches(date: LocalDate, c: CopticDate, p: Int): Boolean = when {
            easterOffset != null -> easterOffset == p
            // Les fêtes à date copte ne sont pas célébrées pendant la Semaine sainte (P − 8 … P).
            coptic != null -> c.month == coptic.first && c.day == coptic.second && p !in -8..0
            gregorian != null -> date.monthValue == gregorian.first && date.dayOfMonth == gregorian.second
            else -> validator?.invoke(date, c) == true
        }
    }

    private companion object {
        fun feast(key: String): DaySelector.(CopticDate) -> Pair<String, Programme> = { "feast:$key" to feastProgramme(key) }
        fun annualRow(key: String): DaySelector.(CopticDate) -> Pair<String, Programme> =
            { "annual:$key" to lectionary.annual.getValue(key) }

        fun isNativityParamoun(date: LocalDate, c: CopticDate): Boolean {
            if (c.month != CopticMonth.KIAHK) return false
            val jan6 = LocalDate.of(date.year, 1, 6)
            if (date == jan6) return true
            return when (jan6.dayOfWeek) {
                DayOfWeek.SUNDAY -> date == jan6.minusDays(1) || date == jan6.minusDays(2)
                DayOfWeek.SATURDAY -> date == jan6.minusDays(1)
                else -> false
            }
        }

        val FEASTS = listOf(
            FeastRule("Christmas", gregorian = 1 to 7, programme = feast("christmas")),
            FeastRule("NativityParamoun", validator = ::isNativityParamoun, programme = annualRow("04-28")),
            FeastRule("Ascension", easterOffset = 39),
            FeastRule("Pentecost", easterOffset = 49),
            FeastRule("ThomasSunday", easterOffset = 7),
            FeastRule("LazarusSaturday", easterOffset = -8, programme = feast("lazarus_saturday")),
            FeastRule("TempleEntrance", coptic = CopticMonth.AMSHIR to 8, programme = feast("temple_entrance")),
            FeastRule("EgyptEntrance", coptic = CopticMonth.BASHANS to 24, programme = feast("egypt_entrance")),
            FeastRule("Annunciation", coptic = CopticMonth.BARAMHAT to 29, programme = feast("annunciation")),
            FeastRule("Cross", coptic = CopticMonth.BARAMHAT to 10, programme = feast("cross_1")),
            FeastRule("Cross", coptic = CopticMonth.TOUT to 17, programme = feast("cross_1")),
            FeastRule("Cross", coptic = CopticMonth.TOUT to 18, programme = feast("cross_2")),
            FeastRule("Cross", coptic = CopticMonth.TOUT to 19, programme = feast("cross_3")),
            FeastRule("FastOfNinevah", easterOffset = -69, programme = feast("nineveh_1")),
            FeastRule("FastOfNinevah", easterOffset = -68, programme = feast("nineveh_2")),
            FeastRule("FastOfNinevah", easterOffset = -67, programme = feast("nineveh_3")),
            FeastRule("Jonas", easterOffset = -66, programme = feast("jonah")),
            FeastRule("PalmSunday", easterOffset = -7, programme = feast("palm_sunday")),
            FeastRule("PaschaMonday", easterOffset = -6, programme = feast("pascha_monday")),
            FeastRule("PaschaTuesday", easterOffset = -5, programme = feast("pascha_tuesday")),
            FeastRule("TheophanyParamoun", coptic = CopticMonth.TOUBA to 10, programme = feast("theophany_paramoun")),
            FeastRule("Theophany", coptic = CopticMonth.TOUBA to 11, programme = annualRow("05-11")),
            FeastRule("WeddingofCana", coptic = CopticMonth.TOUBA to 13, programme = annualRow("05-13")),
            FeastRule("GreatLentPreparationSaturday", easterOffset = -57, programme = feast("lent_preparation_saturday")),
            FeastRule("GreatLentPreparationSunday", easterOffset = -56, programme = feast("lent_preparation_sunday")),
            FeastRule("PaschaWednesday", easterOffset = -4, programme = feast("pascha_wednesday")),
            FeastRule("PaschaThursday", easterOffset = -3, programme = feast("pascha_thursday")),
            FeastRule("PaschaFriday", easterOffset = -2, programme = feast("pascha_friday")),
            FeastRule("PaschaSaturday", easterOffset = -1, programme = feast("pascha_saturday")),
            FeastRule("Easter", easterOffset = 0, programme = feast("easter")),
            FeastRule("Nayrouz", coptic = CopticMonth.TOUT to 1, programme = annualRow("01-01")),
            FeastRule("Circumcision", coptic = CopticMonth.TOUBA to 6),
            FeastRule("Apostles", coptic = CopticMonth.ABIB to 5),
            FeastRule("Transfiguration", coptic = CopticMonth.MESRA to 13),
            FeastRule("StMaryAssumption", coptic = CopticMonth.MESRA to 16),
        )
    }
}
