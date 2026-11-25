package com.copte.katameros.ui

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.copte.katameros.Prefs
import com.copte.katameros.R
import com.copte.katameros.data.ContentStore
import com.copte.katameros.data.ContentUpdater
import com.copte.katameros.databinding.ActivitySettingsBinding
import com.copte.katameros.widget.DayWidget

/** Réglages : langues, taille du texte, thème, version du contenu et mises à jour. */
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

        fillLanguages(binding.primaryGroup, Prefs.PRIMARY_LANGUAGES, withNone = false, selected = prefs.primaryLanguage) { code ->
            prefs.primaryLanguage = code!!
            DayWidget.updateAll(this)
            prefs.applyAppLocale() // recrée les activités dans la nouvelle langue
        }
        refreshSecondary()

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
        binding.checkUpdates.setOnClickListener {
            binding.checkUpdates.isEnabled = false
            ContentUpdater.check(this) { result ->
                if (isDestroyed) return@check
                binding.checkUpdates.isEnabled = true
                val message = when (result) {
                    is ContentUpdater.Result.Updated -> getString(R.string.update_done, result.version)
                    ContentUpdater.Result.UpToDate -> getString(R.string.update_none)
                    is ContentUpdater.Result.Failed -> getString(R.string.update_failed, result.reason)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                if (result is ContentUpdater.Result.Updated) DayWidget.updateAll(this)
                updateContentInfo()
            }
        }
        binding.restoreEmbedded.setOnClickListener {
            ContentUpdater.restoreEmbedded(this)
            DayWidget.updateAll(this)
            updateContentInfo()
        }
    }

    private fun refreshSecondary() {
        val choices = Prefs.ALL_LANGUAGES.filter { it != prefs.primaryLanguage }
        fillLanguages(binding.secondaryGroup, choices, withNone = true, selected = prefs.secondaryLanguage) { code ->
            prefs.secondaryLanguage = code
        }
    }

    private fun fillLanguages(
        group: RadioGroup,
        codes: List<String>,
        withNone: Boolean,
        selected: String?,
        onSelect: (String?) -> Unit,
    ) {
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()
        val entries = (if (withNone) listOf<String?>(null) else emptyList()) + codes
        entries.forEach { code ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = languageName(code)
                tag = code
            }
            group.addView(button)
            if (code == selected) group.check(button.id)
        }
        group.setOnCheckedChangeListener { g, id -> onSelect(g.findViewById<RadioButton>(id)?.tag as String?) }
    }

    private fun languageName(code: String?): String = getString(
        when (code) {
            null -> R.string.lang_none
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
        binding.restoreEmbedded.visibility = if (downloaded) View.VISIBLE else View.GONE
    }
}
