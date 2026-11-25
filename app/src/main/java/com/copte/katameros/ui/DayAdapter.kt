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
 * Liste d'un jour : offices repliables, rubriques, lectures (langue principale puis seconde langue)
 * et commémorations du Synaxaire (titre, texte déplié au toucher).
 */
class DayAdapter(
    private val context: Context,
    private val fontSizeSp: Float,
    private val primaryRtl: Boolean,
    private val secondaryRtl: Boolean,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var blocks: List<Block> = emptyList()
    private var visible: List<Block> = emptyList()
    private val collapsed = mutableSetOf<Int>()
    private val openCommemorations = mutableSetOf<Block.CommemorationBlock>()

    fun submit(newBlocks: List<Block>) {
        blocks = newBlocks
        collapsed.clear()
        openCommemorations.clear()
        refresh()
    }

    private fun refresh() {
        visible = blocks.filter { it is Block.OfficeHeader || it.section !in collapsed }
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount() = visible.size

    /** Position de l'en-tête de l'office [sectionId] (1 Veille, 2 Matin, 3 Messe), ou -1. */
    fun positionOfOffice(sectionId: Int): Int =
        visible.indexOfFirst { it is Block.OfficeHeader && it.sectionId == sectionId }

    override fun getItemViewType(position: Int) = when (visible[position]) {
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
        when (val block = visible[position]) {
            is Block.OfficeHeader -> bindOffice(b as ItemOfficeBinding, block)
            is Block.SubHeader -> {
                b as ItemSubheaderBinding
                b.title.setTextOrHide(block.title)
                b.introduction.setTextOrHide(block.introduction)
                b.introduction.sized(-3f)
                direction(b.root, primaryRtl)
            }
            is Block.ReadingBlock -> {
                b as ItemReadingBinding
                bindText(b.primary, block.primary, primaryRtl)
                val second = block.secondary?.takeIf { it.verses.isNotEmpty() }
                b.divider.visibility = if (second != null) View.VISIBLE else View.GONE
                b.secondary.root.visibility = if (second != null) View.VISIBLE else View.GONE
                if (second != null) bindText(b.secondary, second, secondaryRtl)
            }
            is Block.CommemorationBlock -> bindCommemoration(b as ItemCommemorationBinding, block)
        }
    }

    private fun bindOffice(b: ItemOfficeBinding, block: Block.OfficeHeader) {
        b.title.text = block.title
        val isCollapsed = block.section in collapsed
        b.chevron.rotation = if (isCollapsed) 0f else 180f
        b.root.setOnClickListener {
            if (!collapsed.remove(block.section)) collapsed += block.section
            refresh()
        }
    }

    private fun bindCommemoration(b: ItemCommemorationBinding, block: Block.CommemorationBlock) {
        b.title.text = block.commemoration.title
        b.title.sized(0f)
        val open = block in openCommemorations
        b.chevron.rotation = if (open) 180f else 0f
        b.body.visibility = if (open) View.VISIBLE else View.GONE
        if (open) {
            b.body.text = HtmlCompat.fromHtml(block.commemoration.html, HtmlCompat.FROM_HTML_MODE_COMPACT).trimEnd()
            b.body.sized(0f)
        }
        direction(b.root, primaryRtl)
        b.root.setOnClickListener {
            if (!openCommemorations.remove(block)) openCommemorations += block
            notifyItemChanged(visible.indexOf(block))
        }
    }

    private fun bindText(b: PartReadingTextBinding, text: ReadingText, rtl: Boolean) {
        direction(b.root, rtl)
        b.introduction.setTextOrHide(text.introduction?.let { CopticText.style(context, it) })
        b.introduction.sized(-3f)
        b.reference.text = text.reference
        b.verses.text = CopticText.style(context, versesText(text))
        b.verses.sized(0f)
        b.conclusion.setTextOrHide(text.conclusion?.let { CopticText.style(context, it) })
        b.conclusion.sized(-3f)
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
