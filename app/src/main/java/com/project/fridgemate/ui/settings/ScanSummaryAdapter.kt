package com.project.fridgemate.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.R
import com.project.fridgemate.databinding.ItemScanSummaryBinding

sealed class ScanSummaryItem {
    data class Added(val name: String, val quantity: String) : ScanSummaryItem()
    data class Updated(val name: String, val oldQuantity: String, val newQuantity: String) : ScanSummaryItem()
    data class Removed(val name: String, val quantity: String) : ScanSummaryItem()
}

class ScanSummaryAdapter(
    private val items: List<ScanSummaryItem>
) : RecyclerView.Adapter<ScanSummaryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemScanSummaryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanSummaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        
        when (item) {
            is ScanSummaryItem.Added -> {
                holder.binding.ivTypeIcon.setImageResource(R.drawable.ic_add)
                holder.binding.ivTypeIcon.setColorFilter(ContextCompat.getColor(context, R.color.app_title_green))
                holder.binding.tvChangeText.text = "${item.name} (${item.quantity})"
                holder.binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.light_teal))
            }
            is ScanSummaryItem.Updated -> {
                holder.binding.ivTypeIcon.setImageResource(R.drawable.ic_edit)
                holder.binding.ivTypeIcon.setColorFilter(ContextCompat.getColor(context, R.color.macro_carbs))
                holder.binding.tvChangeText.text = "${item.name}: ${item.oldQuantity} → ${item.newQuantity}"
                holder.binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.warning_yellow_bg))
            }
            is ScanSummaryItem.Removed -> {
                holder.binding.ivTypeIcon.setImageResource(R.drawable.ic_close)
                holder.binding.ivTypeIcon.setColorFilter(ContextCompat.getColor(context, R.color.error_red))
                holder.binding.tvChangeText.text = "${item.name} (${item.quantity})"
                holder.binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.light_gray))
            }
        }
    }

    override fun getItemCount() = items.size
}
