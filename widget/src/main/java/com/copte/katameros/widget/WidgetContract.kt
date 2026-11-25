package com.copte.katameros.widget

/**
 * Règles du widget, fixées dès maintenant pour la phase 5 :
 * - mise à jour à minuit locale (AlarmManager inexact) et sur ACTION_DATE_CHANGED, ACTION_TIME_CHANGED,
 *   ACTION_TIMEZONE_CHANGED et après redémarrage ;
 * - aucune requête réseau : uniquement les données embarquées ou déjà téléchargées ;
 * - un clic ouvre l'application sur le jour affiché (deep link `copte://jour/{date ISO}`).
 */
internal object WidgetContract {
    const val DEEP_LINK_DAY = "copte://jour/"
}
