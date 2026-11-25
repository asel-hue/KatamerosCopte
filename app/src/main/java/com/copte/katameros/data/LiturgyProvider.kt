package com.copte.katameros.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.Configuration
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.copte.calendar.LiturgyContract
import com.copte.katameros.Prefs
import com.copte.katameros.liturgy.IntegrationRow
import com.copte.katameros.ui.AppLabels
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Jour liturgique pour les autres applications coptes (contrat [LiturgyContract], lecture seule).
 * Accès réservé aux applications signées avec la même clé (permission `signature`, voir le manifeste).
 *
 * - `content://com.copte.katameros.liturgy/day/2026-11-25?lang=ar`
 * - `content://com.copte.katameros.liturgy/today`
 */
class LiturgyProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(LiturgyContract.AUTHORITY, "${LiturgyContract.PATH_DAY}/*", DAY)
        addURI(LiturgyContract.AUTHORITY, LiturgyContract.PATH_TODAY, TODAY)
    }

    // Le fournisseur est créé avant Application.onCreate : on initialise le contenu ici aussi.
    override fun onCreate(): Boolean {
        context?.let(ContentStore::init)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val context = requireNotNull(context)
        val date = when (matcher.match(uri)) {
            TODAY -> LocalDate.now()
            DAY -> try {
                LocalDate.parse(uri.lastPathSegment)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Date invalide (AAAA-MM-JJ attendu) : ${uri.lastPathSegment}", e)
            }
            else -> throw IllegalArgumentException("URI inconnue : $uri")
        }
        val requested = uri.getQueryParameter(LiturgyContract.PARAM_LANG)?.takeIf { it in Prefs.PRIMARY_LANGUAGES } ?: Prefs.LANG_FR
        val pack = ContentStore.pack(requested) ?: ContentStore.pack(Prefs.LANG_FR)!!
        val localized = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(pack.code)) }
        )
        val readings = ContentStore.selector().select(date)
        val row = IntegrationRow.build(readings, pack, AppLabels(localized, pack.code).season(readings.day.season))

        val columns = projection?.filter { it in row }?.toTypedArray() ?: LiturgyContract.Columns.ALL.toTypedArray()
        return MatrixCursor(columns, 1).apply { addRow(columns.map { row[it] }) }
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        DAY, TODAY -> "vnd.android.cursor.item/vnd.${LiturgyContract.AUTHORITY}.day"
        else -> null
    }

    // Lecture seule.
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Lecture seule")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Lecture seule")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Lecture seule")

    private companion object {
        const val DAY = 1
        const val TODAY = 2
    }
}
