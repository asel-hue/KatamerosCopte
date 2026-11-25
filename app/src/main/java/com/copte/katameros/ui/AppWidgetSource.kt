package com.copte.katameros.ui

import android.content.Context
import android.content.res.Configuration
import com.copte.katameros.Prefs
import com.copte.katameros.R
import com.copte.katameros.data.ContentStore
import com.copte.katameros.widget.DayInfo
import com.copte.katameros.widget.DayInfoSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Données du widget, dans la langue principale choisie dans l'application. */
object AppWidgetSource : DayInfoSource {

    override fun load(context: Context, date: LocalDate): DayInfo {
        val language = Prefs(context).primaryLanguage
        val locale = Locale.forLanguageTag(language)
        // Le récepteur du widget n'a pas forcément la langue de l'application (Android < 13) : on la force.
        val localized = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) }
        )
        val labels = AppLabels(localized, language)
        val readings = ContentStore.selector().select(date)
        val pack = ContentStore.pack(language) ?: ContentStore.pack(Prefs.LANG_FR)!!
        val c = readings.day.coptic
        return DayInfo(
            copticDate = localized.getString(R.string.coptic_date, c.day, labels.copticMonth(c.month.number), c.year),
            gregorianDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)),
            title = readings.titleKey?.let { pack.texts.feasts[it] } ?: labels.season(readings.day.season),
            saints = pack.synaxarium?.day(c.key).orEmpty().map { it.title.trim().trimEnd('.') },
            rtl = pack.rtl,
        )
    }
}
