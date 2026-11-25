package com.copte.katameros

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.copte.calendar.LiturgicalCalendar
import com.copte.katameros.databinding.ActivityMainBinding
import java.time.LocalDate

/** Écran « Aujourd'hui ». Squelette de la phase 1 : il vérifie seulement que la bibliothèque est branchée. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        // Provisoire (phase 4 : vrais libellés des mois, fêtes et lectures).
        val today = LiturgicalCalendar.dayOf(LocalDate.now())
        binding.placeholder.text = getString(
            R.string.placeholder_today,
            today.coptic.day, today.coptic.month.number, today.coptic.year,
            (listOf(today.season.id) + today.feasts.map { it.id }).joinToString(" · "),
        )
    }
}
