package com.copte.katameros.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.copte.calendar.CopticCalendar
import com.copte.katameros.MainActivity
import com.copte.katameros.Prefs
import com.copte.katameros.R
import com.copte.katameros.data.ContentStore
import com.copte.katameros.databinding.ActivityCalendarBinding
import com.copte.katameros.databinding.ItemCalendarEventBinding
import com.copte.katameros.databinding.ItemCalendarMonthBinding
import com.copte.katameros.liturgy.PlannedEvent
import com.copte.katameros.liturgy.YearPlanner
import com.google.android.material.color.MaterialColors
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Calendrier : fêtes et temps liturgiques d'une année (planning par mois, année au choix),
 * et recherche dans les fêtes et dans les saints du Synaxaire. Un toucher ouvre le jour.
 */
class CalendarActivity : AppCompatActivity() {

    private enum class Kind { FEAST, SEASON, SAINT }

    private data class Entry(val date: LocalDate, val title: String, val subtitle: String, val kind: Kind, val searchText: String)

    private sealed interface Row {
        data class Month(val label: String) : Row
        data class Event(val entry: Entry) : Row
    }

    private lateinit var binding: ActivityCalendarBinding
    private lateinit var prefs: Prefs
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var year = LocalDate.now().year
    private var query = ""
    private var entries: List<Entry> = emptyList()
    private var generation = 0
    private val adapter = RowsAdapter()
    private val locale: Locale get() = resources.configuration.locales[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.list.updatePadding(bottom = bars.bottom + resources.getDimensionPixelSize(R.dimen.list_bottom_padding))
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.previousYear.setOnClickListener { load(year - 1) }
        binding.nextYear.setOnClickListener { load(year + 1) }

        val search = binding.toolbar.menu.findItem(R.id.action_search).actionView as SearchView
        search.queryHint = getString(R.string.calendar_search)
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(text: String?) = true.also { search.clearFocus() }
            override fun onQueryTextChange(text: String?): Boolean {
                query = text.orEmpty().trim()
                render(scrollToToday = false)
                return true
            }
        })

        load(savedInstanceState?.getInt(STATE_YEAR) ?: intent.getIntExtra(EXTRA_YEAR, year))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_YEAR, year)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun load(newYear: Int) {
        year = newYear
        binding.year.text = newYear.toString()
        binding.progress.visibility = View.VISIBLE
        binding.empty.visibility = View.GONE
        val token = ++generation
        val language = prefs.primaryLanguage
        executor.execute {
            val result = runCatching { buildEntries(newYear, language) }
            main.post {
                if (token != generation || isDestroyed) return@post
                binding.progress.visibility = View.GONE
                entries = result.getOrDefault(emptyList())
                render(scrollToToday = true)
            }
        }
    }

    /** Fêtes, temps et saints de l'année, dans la langue de l'application. */
    private fun buildEntries(year: Int, language: String): List<Entry> {
        val pack = ContentStore.pack(language) ?: ContentStore.pack(Prefs.LANG_FR)!!
        val labels = AppLabels(this, language)
        val list = mutableListOf<Entry>()
        for (event in YearPlanner.events(year, ContentStore.selector())) {
            when (event) {
                is PlannedEvent.Feast -> {
                    val title = pack.texts.feasts[event.key] ?: event.key
                    list += entry(event.date, title, event.lastDate, Kind.FEAST)
                }
                is PlannedEvent.SeasonStart -> {
                    val title = labels.season(event.season) ?: continue
                    list += entry(event.date, title, event.lastDate, Kind.SEASON)
                }
            }
        }
        val synaxarium = pack.synaxarium
        if (synaxarium != null) {
            var date = LocalDate.of(year, 1, 1)
            while (date.year == year) {
                val coptic = CopticCalendar.fromGregorian(date)
                synaxarium.day(coptic.key).forEach { c ->
                    list += entry(date, c.title.trim().trimEnd('.'), date, Kind.SAINT)
                }
                date = date.plusDays(1)
            }
        }
        return list.sortedWith(compareBy<Entry> { it.date }.thenBy { it.kind.ordinal })
    }

    private fun entry(date: LocalDate, title: String, lastDate: LocalDate, kind: Kind): Entry {
        val c = CopticCalendar.fromGregorian(date)
        val months = resources.getStringArray(R.array.coptic_months)
        val coptic = getString(R.string.coptic_date, c.day, months[c.month.number - 1], c.year)
        val subtitle = if (lastDate != date) {
            coptic + " · " + getString(R.string.calendar_until, lastDate.format(DateTimeFormatter.ofPattern("d MMMM", locale)))
        } else coptic
        return Entry(date, title, subtitle, kind, normalize(title))
    }

    /**
     * Sans recherche : le planning (fêtes et temps) ; avec recherche : fêtes, temps et saints correspondants.
     * L'année affichée s'ouvre sur aujourd'hui.
     */
    private fun render(scrollToToday: Boolean) {
        val q = normalize(query)
        val shown = if (q.isEmpty()) entries.filter { it.kind != Kind.SAINT } else entries.filter { q in it.searchText }
        val rows = mutableListOf<Row>()
        var month = -1
        for (e in shown) {
            if (e.date.monthValue != month) {
                month = e.date.monthValue
                val label = e.date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                rows += Row.Month(label.replaceFirstChar { it.titlecase(locale) })
            }
            rows += Row.Event(e)
        }
        adapter.submit(rows)
        binding.empty.visibility = if (rows.isEmpty() && binding.progress.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        val layout = binding.list.layoutManager as LinearLayoutManager
        val today = LocalDate.now()
        val target = if (scrollToToday && q.isEmpty() && year == today.year) {
            rows.indexOfFirst { it is Row.Event && !it.entry.date.isBefore(today) }.let { i ->
                // Remonter sur l'en-tête du mois s'il précède directement.
                if (i > 0 && rows[i - 1] is Row.Month) i - 1 else i
            }
        } else 0
        layout.scrollToPositionWithOffset(target.coerceAtLeast(0), 0)
    }

    private fun open(date: LocalDate) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setData(Uri.parse("copte://jour/$date"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    /** Recherche insensible à la casse, aux accents et aux voyelles arabes. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT)

    private inner class RowsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows: List<Row> = emptyList()

        fun submit(newRows: List<Row>) {
            rows = newRows
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Month) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) MonthHolder(ItemCalendarMonthBinding.inflate(inflater, parent, false))
            else EventHolder(ItemCalendarEventBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Month -> (holder as MonthHolder).binding.month.text = row.label
                is Row.Event -> (holder as EventHolder).bind(row.entry)
            }
        }
    }

    private class MonthHolder(val binding: ItemCalendarMonthBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class EventHolder(val binding: ItemCalendarEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(e: Entry) {
            binding.dayNumber.text = e.date.dayOfMonth.toString()
            binding.weekday.text = e.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            binding.title.text = e.title
            binding.subtitle.text = e.subtitle
            val color = MaterialColors.getColor(
                binding.root,
                when (e.kind) {
                    Kind.FEAST -> androidx.appcompat.R.attr.colorPrimary
                    Kind.SEASON -> com.google.android.material.R.attr.colorSecondary
                    Kind.SAINT -> com.google.android.material.R.attr.colorOnSurface
                },
            )
            binding.title.setTextColor(color)
            binding.dayNumber.setTextColor(color)
            binding.title.setTypeface(null, if (e.kind == Kind.FEAST) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            val today = e.date == LocalDate.now()
            binding.dateBox.setBackgroundResource(if (today) R.drawable.bg_today else 0)
            binding.root.setOnClickListener { open(e.date) }
        }
    }

    companion object {
        const val EXTRA_YEAR = "year"
        private const val STATE_YEAR = "year"
        private val MARKS = Regex("\\p{Mn}+")
    }
}
