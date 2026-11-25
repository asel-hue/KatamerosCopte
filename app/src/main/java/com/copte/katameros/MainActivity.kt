package com.copte.katameros

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.copte.calendar.LiturgyContract
import com.copte.katameros.data.ContentStore
import com.copte.katameros.databinding.ActivityMainBinding
import com.copte.katameros.liturgy.Block
import com.copte.katameros.liturgy.DayRenderer
import com.copte.katameros.liturgy.Section
import com.copte.katameros.ui.AppLabels
import com.copte.katameros.ui.CalendarActivity
import com.copte.katameros.ui.DayAdapter
import com.copte.katameros.ui.DayHeaderAdapter
import com.copte.katameros.ui.FoldState
import com.copte.katameros.ui.SettingsActivity
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.Executors

/**
 * Écran du jour : date copte, fête ou temps liturgique, puis toutes les lectures (Veille, Matin, Messe
 * ou heures de la Semaine sainte) avec le Synaxaire. Ouvert aussi par le lien `copte://jour/AAAA-MM-JJ`.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var date: LocalDate = LocalDate.now()
    private var shownSettings: String? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private val headerAdapter = DayHeaderAdapter(
        onPrevious = { show(date.minusDays(1)) },
        onNext = { show(date.plusDays(1)) },
        onPickDate = { pickDate() },
    )
    private var dayAdapter: DayAdapter? = null
    /** Replis du jour affiché, conservés quand on change la langue des lectures. */
    private val fold = FoldState()
    /** Office à atteindre après affichage (lien `copte://lectures/…`), ou `null`. */
    private var pendingOffice: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (prefs.applyAppLocale()) return // l'activité va être recréée dans la bonne langue
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.list.updatePadding(bottom = bars.bottom + resources.getDimensionPixelSize(R.dimen.list_bottom_padding))
            insets
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_language -> chooseLanguage()
                R.id.action_calendar -> startActivity(Intent(this, CalendarActivity::class.java).putExtra(CalendarActivity.EXTRA_YEAR, date.year))
                R.id.action_today -> show(LocalDate.now())
                R.id.action_expand_all -> dayAdapter?.setAllExpanded(true)
                R.id.action_collapse_all -> dayAdapter?.setAllExpanded(false)
                R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        date = savedInstanceState?.getString(STATE_DATE)?.let(LocalDate::parse) ?: dateFrom(intent) ?: LocalDate.now()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dateFrom(intent)?.let(::show)
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        // Recharge si les réglages ont changé (langue, taille du texte, contenu) ou au premier affichage.
        val settings = settingsKey()
        if (settings != shownSettings) {
            shownSettings = settings
            show(date)
        }
    }

    private fun settingsKey() = "${prefs.primaryLanguage}|${prefs.displayLanguage}|${prefs.fontSize}|${ContentStore.source}"

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Pas de date à sauver si l'activité a été recréée avant tout affichage (changement de langue) :
        // sinon la date du jour masquerait celle d'un lien copte://.
        if (::binding.isInitialized) outState.putString(STATE_DATE, date.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    /**
     * Liens du contrat d'intégration : `copte://jour/AAAA-MM-JJ` et `copte://lectures/AAAA-MM-JJ/office`.
     * Pour un office, on note la section à atteindre une fois les lectures affichées.
     */
    private fun dateFrom(intent: Intent?): LocalDate? {
        val data = intent?.data ?: return null
        if (data.scheme != "copte") return null
        val segments = data.pathSegments
        return when (data.host) {
            "jour" -> runCatching { LocalDate.parse(segments.firstOrNull()) }.getOrNull()
            "lectures" -> runCatching { LocalDate.parse(segments.firstOrNull()) }.getOrNull()?.also {
                pendingOffice = when (LiturgyContract.Office.fromSlug(segments.getOrNull(1))) {
                    LiturgyContract.Office.VESPERS -> Section.VESPERS
                    LiturgyContract.Office.MATINS -> Section.MATINS
                    LiturgyContract.Office.LITURGY -> Section.LITURGY
                    null -> null
                }
            }
            else -> null
        }
    }

    private fun pickDate() {
        val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.choose_date)
            .setSelection(millis)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            show(Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(supportFragmentManager, "date")
    }

    /** Bouton globe : langue des lectures parmi celles du contenu (la langue de l'application en premier). */
    private fun chooseLanguage() {
        val anchor = binding.toolbar.findViewById<View>(R.id.action_language) ?: binding.toolbar
        val current = prefs.displayLanguage ?: prefs.primaryLanguage
        val codes = listOf(prefs.primaryLanguage) + Prefs.ALL_LANGUAGES.filter { it != prefs.primaryLanguage }
        PopupMenu(this, anchor).apply {
            codes.forEachIndexed { i, code ->
                menu.add(0, i, i, CopticText.style(this@MainActivity, languageName(code), extraSizeSp = 0f)).apply {
                    isCheckable = true
                    isChecked = code == current
                }
            }
            menu.setGroupCheckable(0, true, true)
            setOnMenuItemClickListener { item ->
                val chosen = codes[item.itemId]
                if (chosen != current) {
                    prefs.displayLanguage = chosen
                    shownSettings = settingsKey()
                    show(date, keepFold = true)
                }
                true
            }
            show()
        }
    }

    /**
     * Affiche [newDate]. [keepFold] : même jour, autre langue : on garde les replis et la position.
     */
    private fun show(newDate: LocalDate, keepFold: Boolean = false) {
        if (!keepFold || newDate != date) fold.reset()
        date = newDate
        val token = ++generation
        val primaryCode = prefs.primaryLanguage
        val displayCode = prefs.displayLanguage
        val scroll = if (keepFold) currentScroll() else null
        binding.progress.visibility = View.VISIBLE
        binding.error.visibility = View.GONE
        executor.execute {
            val started = System.nanoTime()
            val result = runCatching {
                val readings = ContentStore.selector().select(newDate)
                val primary = ContentStore.pack(primaryCode) ?: ContentStore.pack(Prefs.LANG_FR)!!
                val chosen = displayCode?.let(ContentStore::pack)
                // Langue complète (fr, ar, en) : tout le jour passe dans cette langue (titres, Synaxaire).
                // Copte, qui n'a que les versets : titres et formules restent dans la langue de l'application.
                val full = chosen != null && chosen.texts.readings.isNotEmpty()
                val shown = if (full) chosen!! else primary
                val versesOnly = if (full) null else chosen
                val blocks = DayRenderer(shown, versesOnly, AppLabels(localized(shown.code), shown.code)).render(readings)
                Loaded(
                    day = readings.day,
                    title = readings.titleKey?.let { primary.texts.feasts[it] },
                    blocks = blocks,
                    rtl = shown.rtl,
                    secondaryRtl = versesOnly?.rtl ?: false,
                    preferSecondary = versesOnly != null,
                    scroll = scroll,
                )
            }
            Log.i(TAG, "Lectures du $newDate (${result.getOrNull()?.blocks?.size} éléments) en ${(System.nanoTime() - started) / 1_000_000} ms")
            main.post {
                if (token != generation || isDestroyed) return@post
                binding.progress.visibility = View.GONE
                result.onSuccess(::display).onFailure {
                    Log.e(TAG, "Lectures du $newDate", it)
                    binding.error.visibility = View.VISIBLE
                    binding.list.adapter = ConcatAdapter(headerAdapter)
                }
            }
        }
        updateHeader(newDate, null, null)
    }

    private class Loaded(
        val day: com.copte.calendar.LiturgicalDay,
        val title: String?,
        val blocks: List<Block>,
        val rtl: Boolean,
        val secondaryRtl: Boolean,
        val preferSecondary: Boolean,
        val scroll: Pair<Int, Int>?,
    )

    private fun display(loaded: Loaded) {
        updateHeader(date, loaded.title, AppLabels(this, prefs.primaryLanguage).season(loaded.day.season))
        val adapter = DayAdapter(this, prefs.fontSize, loaded.rtl, loaded.secondaryRtl, loaded.preferSecondary, fold)
        dayAdapter = adapter
        binding.list.adapter = ConcatAdapter(headerAdapter, adapter)
        adapter.submit(loaded.blocks)
        val layout = binding.list.layoutManager as LinearLayoutManager
        loaded.scroll?.let { (position, offset) ->
            layout.scrollToPositionWithOffset(position, offset)
            return
        }
        // Lien vers un office : aller à son en-tête (+1 pour l'en-tête du jour), sinon en haut.
        val target = pendingOffice?.let { id -> adapter.positionOfOffice(id) }?.takeIf { it >= 0 }?.plus(headerAdapter.itemCount)
        pendingOffice = null
        layout.scrollToPositionWithOffset(target ?: 0, 0)
    }

    /** Position (élément, décalage) du haut de la liste, pour la retrouver après un changement de langue. */
    private fun currentScroll(): Pair<Int, Int>? {
        val layout = binding.list.layoutManager as? LinearLayoutManager ?: return null
        val position = layout.findFirstVisibleItemPosition().takeIf { it >= 0 } ?: return null
        val top = layout.findViewByPosition(position)?.top ?: 0
        return position to (top - binding.list.paddingTop)
    }

    private fun languageName(code: String) = getString(
        when (code) {
            Prefs.LANG_AR -> R.string.lang_ar
            Prefs.LANG_EN -> R.string.lang_en
            Prefs.LANG_COP -> R.string.lang_cop
            else -> R.string.lang_fr
        }
    )

    /** Contexte dans la langue [code] (noms des mois et formules du Synaxaire dans la langue affichée). */
    private fun localized(code: String) = createConfigurationContext(
        android.content.res.Configuration(resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(code)) }
    )

    private fun updateHeader(day: LocalDate, feast: String?, season: String?) {
        val coptic = com.copte.calendar.CopticCalendar.fromGregorian(day)
        val months = resources.getStringArray(R.array.coptic_months)
        headerAdapter.update(
            DayHeaderAdapter.Header(
                copticDate = getString(R.string.coptic_date, coptic.day, months[coptic.month.number - 1], coptic.year),
                gregorianDate = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(resources.configuration.locales[0])),
                feast = feast,
                season = season,
            )
        )
    }

    private companion object {
        const val TAG = "MainActivity"
        const val STATE_DATE = "date"
    }
}
