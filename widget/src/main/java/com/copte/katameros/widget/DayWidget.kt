package com.copte.katameros.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZonedDateTime

/** Ce que le widget affiche pour un jour, déjà traduit dans la langue de l'application. */
data class DayInfo(
    /** « 16 Hatour 1743 » */
    val copticDate: String,
    /** « 25 novembre 2026 » */
    val gregorianDate: String,
    /** Fête du jour, sinon temps liturgique, sinon `null`. */
    val title: String?,
    /** Titres des commémorations du Synaxaire, dans l'ordre. */
    val saints: List<String>,
    val rtl: Boolean,
)

/** Fournie par l'application (accès au contenu et aux libellés traduits). Le widget ne lit rien lui-même. */
fun interface DayInfoSource {
    fun load(context: Context, date: LocalDate): DayInfo
}

object DayWidget {
    /** Renseignée par l'application au démarrage (`Application.onCreate`), avant toute diffusion vers le widget. */
    @Volatile
    var source: DayInfoSource? = null

    /** Redessine tous les widgets posés (après un changement de langue ou de contenu). */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, DayWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(component)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
    }

    /** Prochain minuit local, plus une marge pour être sûr d'être sur le nouveau jour. */
    fun nextRefresh(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(MARGIN_SECONDS)

    internal const val MARGIN_SECONDS = 30L
}
