package com.copte.katameros.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.copte.katameros.databinding.ItemDayHeaderBinding

/**
 * En-tête du jour (date copte, date grégorienne, fête, temps), centré, premier élément de la liste :
 * flèches de part et d'autre pour changer de jour, toucher sur la date pour ouvrir le calendrier.
 */
class DayHeaderAdapter(
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
    private val onPickDate: () -> Unit,
) : RecyclerView.Adapter<DayHeaderAdapter.Holder>() {

    data class Header(val copticDate: String, val gregorianDate: String, val feast: String?, val season: String?)

    class Holder(val binding: ItemDayHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    private var header: Header? = null

    fun update(value: Header) {
        val had = header != null
        header = value
        if (had) notifyItemChanged(0) else notifyItemInserted(0)
    }

    override fun getItemCount() = if (header == null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemDayHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)).apply {
            binding.previous.setOnClickListener { onPrevious() }
            binding.next.setOnClickListener { onNext() }
            binding.dateBlock.setOnClickListener { onPickDate() }
        }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val h = header ?: return
        with(holder.binding) {
            copticDate.text = h.copticDate
            gregorianDate.text = h.gregorianDate
            feastTitle.text = h.feast
            feastTitle.visibility = if (h.feast.isNullOrBlank()) View.GONE else View.VISIBLE
            season.text = h.season
            season.visibility = if (h.season.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }
}
