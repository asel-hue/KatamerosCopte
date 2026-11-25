package com.copte.katameros.ui

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.copte.katameros.CopticText
import com.copte.katameros.Prefs
import com.copte.katameros.R
import com.copte.katameros.data.ContentStore
import com.copte.katameros.data.ContentUpdater
import com.copte.katameros.databinding.ActivitySettingsBinding
import com.copte.katameros.widget.DayWidget
import com.google.android.material.radiobutton.MaterialRadioButton

/**
 * Réglages : langue de l'application, taille du texte, thème, version du contenu.
 * Les mises à jour du contenu sont automatiques (une fois par semaine), comme dans LivreAGPIA.
 * La langue des lectures se choisit avec le bouton globe de l'écran du jour.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        Prefs.PRIMARY_LANGUAGES.forEach { code ->
            val button = MaterialRadioButton(this).apply {
                id = View.generateViewId()
                text = CopticText.style(this@SettingsActivity, languageName(code), extraSizeSp = 0f)
                tag = code
            }
            binding.primaryGroup.addView(button)
            if (code == prefs.primaryLanguage) binding.primaryGroup.check(button.id)
        }
        binding.primaryGroup.setOnCheckedChangeListener { g, id ->
            val code = g.findViewById<RadioButton>(id)?.tag as? String ?: return@setOnCheckedChangeListener
            prefs.primaryLanguage = code
            DayWidget.updateAll(this)
            prefs.applyAppLocale() // recrée les activités dans la nouvelle langue
        }

        binding.fontSize.value = prefs.fontSize.coerceIn(Prefs.MIN_FONT_SIZE, Prefs.MAX_FONT_SIZE)
        updateFontLabel()
        binding.fontSize.addOnChangeListener { _, value, _ ->
            prefs.fontSize = value
            updateFontLabel()
        }

        binding.themeGroup.check(
            when (prefs.themeMode) {
                Prefs.THEME_LIGHT -> R.id.themeLight
                Prefs.THEME_DARK -> R.id.themeDark
                else -> R.id.themeSystem
            }
        )
        binding.themeGroup.setOnCheckedChangeListener { _, id ->
            prefs.themeMode = when (id) {
                R.id.themeLight -> Prefs.THEME_LIGHT
                R.id.themeDark -> Prefs.THEME_DARK
                else -> Prefs.THEME_SYSTEM
            }
            prefs.applyNightMode()
        }

        updateContentInfo()
        binding.restoreEmbedded.setOnClickListener {
            ContentUpdater.restoreEmbedded(this)
            DayWidget.updateAll(this)
            updateContentInfo()
        }
    }

    private fun languageName(code: String): String = getString(
        when (code) {
            Prefs.LANG_AR -> R.string.lang_ar
            Prefs.LANG_EN -> R.string.lang_en
            Prefs.LANG_COP -> R.string.lang_cop
            else -> R.string.lang_fr
        }
    )

    private fun updateFontLabel() {
        binding.fontSizeLabel.text = getString(R.string.settings_font_size, prefs.fontSize.toInt())
    }

    private fun updateContentInfo() {
        val downloaded = ContentStore.source == ContentStore.Source.DOWNLOADED
        val version = runCatching { ContentStore.manifest().contentVersion }.getOrDefault(0)
        binding.contentVersion.text = getString(
            R.string.content_version,
            version,
            getString(if (downloaded) R.string.content_downloaded else R.string.content_embedded),
        )
        binding.restoreEmbedded.isEnabled = downloaded
    }
}
