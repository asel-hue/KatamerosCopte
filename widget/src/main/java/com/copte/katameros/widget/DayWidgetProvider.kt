package com.copte.katameros.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.copte.calendar.CopticCalendar
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Widget « Jour copte » : date copte, date grégorienne, fête ou temps liturgique, saints du Synaxaire.
 *
 * Mise à jour sans réseau ni service permanent :
 * - alarme inexacte à minuit + 30 s (`setAndAllowWhileIdle`, aucune permission d'alarme exacte) ;
 * - changements d'heure, de fuseau ou de langue, redémarrage et mise à jour de l'application ;
 * - `updatePeriodMillis` de secours (6 h) si l'alarme a été perdue (arrêt forcé).
 * Un toucher ouvre l'application sur le jour affiché (`copte://jour/AAAA-MM-JJ`).
 */
class DayWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MIDNIGHT,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> refresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context)

    override fun onEnabled(context: Context) = scheduleMidnight(context)

    override fun onDisabled(context: Context) = cancelMidnight(context)

    /** Lecture du contenu hors du thread principal (goAsync : jusqu'à 10 s accordées au récepteur). */
    private fun refresh(context: Context) {
        val pending = goAsync()
        Thread({
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(android.content.ComponentName(context, DayWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    val date = LocalDate.now()
                    val info = load(context, date)
                    ids.forEach { manager.updateAppWidget(it, views(context, date, info)) }
                    scheduleMidnight(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mise à jour du widget impossible", e)
            } finally {
                pending.finish()
            }
        }, "day-widget").start()
    }

    private fun load(context: Context, date: LocalDate): DayInfo =
        runCatching { DayWidget.source?.load(context, date) }.onFailure { Log.w(TAG, "Source du widget en échec", it) }.getOrNull()
            ?: fallback(date)

    /** Sans contenu disponible : au moins la date copte en chiffres. */
    private fun fallback(date: LocalDate): DayInfo {
        val c = CopticCalendar.fromGregorian(date)
        return DayInfo(
            copticDate = "${c.day}/${c.month.number}/${c.year}",
            gregorianDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
            title = null,
            saints = emptyList(),
            rtl = false,
        )
    }

    private fun views(context: Context, date: LocalDate, info: DayInfo): RemoteViews {
        val small = build(context, R.layout.widget_day_small, date, info)
        // Autant de saints que la hauteur le permet (2 lignes chacun), sans ligne coupée en bas.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(
                mapOf(SizeF(110f, 40f) to small) +
                    SAINTS_BY_HEIGHT.map { (height, saints) ->
                        SizeF(180f, height) to build(context, R.layout.widget_day_medium, date, info, saints)
                    }
            )
        } else {
            build(context, R.layout.widget_day_medium, date, info, saintsFor(context, ids = null))
        }
    }

    /** Avant Android 12 : nombre de saints d'après la hauteur maximale déclarée par le lanceur. */
    private fun saintsFor(context: Context, ids: IntArray?): Int {
        val manager = AppWidgetManager.getInstance(context)
        val all = ids ?: manager.getAppWidgetIds(android.content.ComponentName(context, DayWidgetProvider::class.java))
        val height = all.minOfOrNull { manager.getAppWidgetOptions(it).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) } ?: 0
        return SAINTS_BY_HEIGHT.lastOrNull { height >= it.first }?.second ?: 1
    }

    private fun build(context: Context, layout: Int, date: LocalDate, info: DayInfo, maxSaints: Int = 0): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            setInt(R.id.widget_root, "setLayoutDirection", if (info.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
            setTextViewText(R.id.widget_coptic_date, info.copticDate)
            setTextViewText(R.id.widget_gregorian_date, info.gregorianDate)
            setTextViewText(R.id.widget_title, info.title ?: "")
            setViewVisibility(R.id.widget_title, if (info.title.isNullOrBlank()) View.GONE else View.VISIBLE)
            if (layout == R.layout.widget_day_medium) {
                val saints = info.saints.take(maxSaints).joinToString("\n") { "• $it" }
                setTextViewText(R.id.widget_saints, saints)
                setInt(R.id.widget_saints, "setMaxLines", maxSaints * 2)
                setViewVisibility(R.id.widget_saints, if (saints.isEmpty()) View.GONE else View.VISIBLE)
            }
            setOnClickPendingIntent(R.id.widget_root, openDay(context, date))
        }

    private fun openDay(context: Context, date: LocalDate): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WidgetContract.DEEP_LINK_DAY + date))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Deux alarmes, sans permission d'alarme exacte :
     * - fenêtre de 10 min après minuit, non réveillante : précise téléphone allumé, sinon délivrée dès que l'écran se rallume ;
     * - `setAndAllowWhileIdle` en secours : passe même en Doze, mais Android l'accorde avec une fenêtre pouvant aller à 1 h.
     */
    private fun scheduleMidnight(context: Context) {
        val at = DayWidget.nextRefresh(ZonedDateTime.now()).toInstant().toEpochMilli()
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setWindow(AlarmManager.RTC, at, WINDOW_MS, midnightIntent(context, REQUEST_WINDOW))
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, at, midnightIntent(context, REQUEST_IDLE))
    }

    private fun cancelMidnight(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(midnightIntent(context, REQUEST_WINDOW))
        alarms.cancel(midnightIntent(context, REQUEST_IDLE))
    }

    private fun midnightIntent(context: Context, request: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        request,
        Intent(context, DayWidgetProvider::class.java).setAction(ACTION_MIDNIGHT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "DayWidget"
        const val ACTION_MIDNIGHT = "com.copte.katameros.widget.MIDNIGHT"
        private const val REQUEST_IDLE = 1
        private const val REQUEST_WINDOW = 2
        private const val WINDOW_MS = 10 * 60 * 1000L
        /** Hauteur minimale (dp) → nombre de saints affichés : en-tête ≈ 95 dp, puis ≈ 40 dp par saint. */
        private val SAINTS_BY_HEIGHT = listOf(100f to 1, 190f to 2, 250f to 3)
    }
}
