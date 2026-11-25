package com.copte.katameros

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.copte.katameros.data.ContentStore
import com.copte.katameros.databinding.ActivityMainBinding
import com.copte.katameros.liturgy.Block
import com.copte.katameros.liturgy.DayRenderer
import com.copte.katameros.liturgy.Section
import com.copte.calendar.LiturgyContract
import com.copte.katameros.ui.AppLabels
import com.copte.katameros.ui.DayAdapter
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
                R.id.action_previous -> show(date.minusDays(1))
                R.id.action_next -> show(date.plusDays(1))
                R.id.action_today -> show(LocalDate.now())
                R.id.action_pick_date -> pickDate()
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
        // Recharge si les réglages ont changé (langues, taille du texte) ou au premier affichage.
        val settings = "${prefs.primaryLanguage}|${prefs.secondaryLanguage}|${prefs.fontSize}|${ContentStore.source}"
        if (settings != shownSettings) {
            shownSettings = settings
            show(date)
        }
    }

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

    private fun show(newDate: LocalDate) {
        date = newDate
        val token = ++generation
        val primaryCode = prefs.primaryLanguage
        val secondaryCode = prefs.secondaryLanguage
        val labels = AppLabels(this, primaryCode)
        binding.progress.visibility = View.VISIBLE
        binding.error.visibility = View.GONE
        executor.execute {
            val started = System.nanoTime()
            val result = runCatching {
                val readings = ContentStore.selector().select(newDate)
                val primary = ContentStore.pack(primaryCode) ?: ContentStore.pack(Prefs.LANG_FR)!!
                val secondary = secondaryCode?.let(ContentStore::pack)
                val blocks = DayRenderer(primary, secondary, labels).render(readings)
                Loaded(readings.day, readings.titleKey?.let { primary.texts.feasts[it] }, blocks, primary.rtl, secondary?.rtl ?: false)
            }
            Log.i(TAG, "Lectures du $newDate (${result.getOrNull()?.blocks?.size} éléments) en ${(System.nanoTime() - started) / 1_000_000} ms")
            main.post {
                if (token != generation || isDestroyed) return@post
                binding.progress.visibility = View.GONE
                result.onSuccess(::display).onFailure {
                    Log.e(TAG, "Lectures du $newDate", it)
                    binding.error.visibility = View.VISIBLE
                    binding.list.adapter = null
                }
            }
        }
        updateHeader(newDate, null, null)
    }

    private class Loaded(
        val day: com.copte.calendar.LiturgicalDay,
        val title: String?,
        val blocks: List<Block>,
        val primaryRtl: Boolean,
        val secondaryRtl: Boolean,
    )

    private fun display(loaded: Loaded) {
        updateHeader(date, loaded.title, AppLabels(this, prefs.primaryLanguage).season(loaded.day.season))
        val adapter = DayAdapter(this, prefs.fontSize, loaded.primaryRtl, loaded.secondaryRtl)
        binding.list.adapter = adapter
        adapter.submit(loaded.blocks)
        // Lien vers un office : aller à son en-tête (s'il existe ce jour-là), sinon en haut.
        val target = pendingOffice?.let { id -> adapter.positionOfOffice(id) }?.takeIf { it >= 0 }
        pendingOffice = null
        (binding.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(target ?: 0, 0)
    }

    private fun updateHeader(day: LocalDate, feast: String?, season: String?) {
        val coptic = com.copte.calendar.CopticCalendar.fromGregorian(day)
        val months = resources.getStringArray(R.array.coptic_months)
        binding.copticDate.text = getString(R.string.coptic_date, coptic.day, months[coptic.month.number - 1], coptic.year)
        binding.gregorianDate.text = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(resources.configuration.locales[0]))
        binding.feastTitle.visibility = if (feast.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.feastTitle.text = feast
        binding.season.visibility = if (season.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.season.text = season
    }

    private companion object {
        const val TAG = "MainActivity"
        const val STATE_DATE = "date"
    }
}
