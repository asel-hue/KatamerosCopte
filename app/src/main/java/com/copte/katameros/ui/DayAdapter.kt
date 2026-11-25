package com.copte.katameros.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.copte.katameros.CopticText
import com.copte.katameros.databinding.ItemCommemorationBinding
import com.copte.katameros.databinding.ItemOfficeBinding
import com.copte.katameros.databinding.ItemReadingBinding
import com.copte.katameros.databinding.ItemSubheaderBinding
import com.copte.katameros.databinding.PartReadingTextBinding
import com.copte.katameros.liturgy.Block
import com.copte.katameros.liturgy.ReadingText
import com.google.android.material.color.MaterialColors

/**
 * État replié / déplié d'un jour, conservé quand on bascule de langue.
 * Par défaut : offices ouverts, rubriques (Psaume et Évangile, Épître…) repliées.
 */
class FoldState {
    val collapsedOffices = mutableSetOf<Int>()
    /** Rubriques ouvertes, clé « office:rang de la rubrique dans l'office ». */
    val openSubSections = mutableSetOf<String>()
    val openCommemorations = mutableSetOf<String>()

    fun reset() {
        collapsedOffices.clear()
        openSubSections.clear()
        openCommemorations.clear()
    }
}

/**
 * Liste d'un jour : offices et rubriques sous forme de titres centrés repliables, lectures,
 * commémorations du Synaxaire (titre, texte déplié au toucher).
 *
 * @param preferSecondary afficher le texte de la seconde langue des lectures (bascule vers le copte,
 *   qui n'a que les versets : introductions et conclusions restent dans la langue principale).
 */
class DayAdapter(
    private val context: Context,
    private val fontSizeSp: Float,
    private val rtl: Boolean,
    private val secondaryRtl: Boolean,
    private val preferSecondary: Boolean,
    private val fold: FoldState,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var blocks: List<Block> = emptyList()
    /** Clé de la rubrique de chaque élément (« office:rang »), `null` pour un en-tête d'office. */
    private var keys: List<String?> = emptyList()
    private var visible: List<Int> = emptyList()

    fun submit(newBlocks: List<Block>) {
        blocks = newBlocks
        var rank = -1
        var lastSection = -1
        keys = newBlocks.map { b ->
            if (b.section != lastSection) { lastSection = b.section; rank = -1 }
            when (b) {
                is Block.OfficeHeader -> null
                is Block.SubHeader -> { rank++; "${b.section}:$rank" }
                else -> "${b.section}:${rank.coerceAtLeast(0)}"
            }
        }
        refresh()
    }

    /** Ouvre ou ferme tout (offices et rubriques). */
    fun setAllExpanded(expanded: Boolean) {
        fold.collapsedOffices.clear()
        fold.openSubSections.clear()
        if (expanded) {
            blocks.indices.filter { blocks[it] is Block.SubHeader }.mapNotNullTo(fold.openSubSections) { keys[it] }
        } else {
            blocks.filterIsInstance<Block.OfficeHeader>().mapTo(fold.collapsedOffices) { it.section }
        }
        refresh()
    }

    private fun refresh() {
        visible = blocks.indices.filter { i ->
            val b = blocks[i]
            when {
                b is Block.OfficeHeader -> true
                b.section in fold.collapsedOffices -> false
                b is Block.SubHeader -> true
                else -> keys[i] in fold.openSubSections
            }
        }
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount() = visible.size

    /** Position de l'en-tête de l'office [sectionId] (1 Veille, 2 Matin, 3 Messe), ou -1. */
    fun positionOfOffice(sectionId: Int): Int =
        visible.indexOfFirst { (blocks[it] as? Block.OfficeHeader)?.sectionId == sectionId }

    override fun getItemViewType(position: Int) = when (blocks[visible[position]]) {
        is Block.OfficeHeader -> 0
        is Block.SubHeader -> 1
        is Block.ReadingBlock -> 2
        is Block.CommemorationBlock -> 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> Holder(ItemOfficeBinding.inflate(inflater, parent, false))
            1 -> Holder(ItemSubheaderBinding.inflate(inflater, parent, false))
            2 -> Holder(ItemReadingBinding.inflate(inflater, parent, false))
            else -> Holder(ItemCommemorationBinding.inflate(inflater, parent, false))
        }
    }

    private class Holder(val binding: androidx.viewbinding.ViewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val b = (holder as Holder).binding
        val index = visible[position]
        when (val block = blocks[index]) {
            is Block.OfficeHeader -> bindOffice(b as ItemOfficeBinding, block)
            is Block.SubHeader -> bindSubHeader(b as ItemSubheaderBinding, block, keys[index]!!)
            is Block.ReadingBlock -> bindReading(b as ItemReadingBinding, block)
            is Block.CommemorationBlock -> bindCommemoration(b as ItemCommemorationBinding, block, "${keys[index]}:$index")
        }
    }

    private fun bindOffice(b: ItemOfficeBinding, block: Block.OfficeHeader) {
        b.title.text = block.title
        val open = block.section !in fold.collapsedOffices
        b.chevron.rotation = if (open) 180f else 0f
        b.root.setOnClickListener {
            if (!fold.collapsedOffices.remove(block.section)) fold.collapsedOffices += block.section
            refresh()
        }
    }

    private fun bindSubHeader(b: ItemSubheaderBinding, block: Block.SubHeader, key: String) {
        direction(b.root, rtl)
        b.title.text = block.title ?: ""
        val open = key in fold.openSubSections
        b.chevron.rotation = if (open) 180f else 0f
        // L'introduction (« Levons-nous avec crainte de Dieu… ») fait partie du contenu : visible une fois ouvert.
        b.introduction.setTextOrHide(if (open) block.introduction else null)
        b.introduction.sized(-3f)
        b.header.setOnClickListener {
            if (!fold.openSubSections.remove(key)) fold.openSubSections += key
            refresh()
        }
    }

    private fun bindReading(b: ItemReadingBinding, block: Block.ReadingBlock) {
        val second = block.secondary?.takeIf { preferSecondary && it.verses.isNotEmpty() }
        if (second != null) {
            // Copte : seuls les versets existent, on garde les formules de la langue principale.
            val text = second.copy(
                introduction = second.introduction ?: block.primary.introduction,
                conclusion = second.conclusion ?: block.primary.conclusion,
            )
            bindText(b.primary, text, secondaryRtl, introRtl = rtl)
        } else {
            bindText(b.primary, block.primary, rtl, introRtl = rtl)
        }
        b.divider.visibility = View.GONE
        b.secondary.root.visibility = View.GONE
    }

    private fun bindCommemoration(b: ItemCommemorationBinding, block: Block.CommemorationBlock, key: String) {
        b.title.text = block.commemoration.title
        b.title.sized(-1f)
        val open = key in fold.openCommemorations
        b.chevron.rotation = if (open) 180f else 0f
        b.body.visibility = if (open) View.VISIBLE else View.GONE
        if (open) {
            b.body.text = HtmlCompat.fromHtml(block.commemoration.html, HtmlCompat.FROM_HTML_MODE_COMPACT).trimEnd()
            b.body.sized(0f)
        }
        direction(b.root, rtl)
        b.root.setOnClickListener {
            if (!fold.openCommemorations.remove(key)) fold.openCommemorations += key
            notifyItemChanged(visible.indexOf(blocks.indexOf(block)))
        }
    }

    private fun bindText(b: PartReadingTextBinding, text: ReadingText, textRtl: Boolean, introRtl: Boolean) {
        direction(b.root, textRtl)
        b.introduction.setTextOrHide(text.introduction?.let { CopticText.style(context, it) })
        b.introduction.sized(-3f)
        b.conclusion.setTextOrHide(text.conclusion?.let { CopticText.style(context, it) })
        b.conclusion.sized(-3f)
        // Formules (introduction, conclusion) dans le sens de leur propre langue.
        listOf(b.introduction, b.conclusion).forEach { direction(it, introRtl) }
        b.reference.text = text.reference
        b.verses.text = CopticText.style(context, versesText(text))
        b.verses.sized(0f)
    }

    /** Versets à la suite, numéros en exposant ; « chapitre:verset » quand le chapitre change. */
    private fun versesText(text: ReadingText): CharSequence {
        val color = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)
        val sb = SpannableStringBuilder()
        var chapter = -1
        for (v in text.verses) {
            if (sb.isNotEmpty()) sb.append(' ')
            val label = if (v.chapter != chapter) "${v.chapter}:${v.number}" else "${v.number}"
            chapter = v.chapter
            val start = sb.length
            sb.append(label)
            sb.setSpan(SuperscriptSpan(), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.65f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(' ').append(v.text)
        }
        return sb
    }

    private fun TextView.setTextOrHide(value: CharSequence?) {
        visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        text = value
    }

    private fun TextView.sized(delta: Float) = setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp + delta)

    private fun direction(view: View, rtl: Boolean) {
        view.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        view.textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
    }
}
