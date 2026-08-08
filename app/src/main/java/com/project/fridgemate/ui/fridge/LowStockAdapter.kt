package com.project.fridgemate.ui.fridge

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.R
import com.project.fridgemate.databinding.ItemLowStockBinding

class LowStockAdapter(private val items: List<FridgeItem.Product>) :
    RecyclerView.Adapter<LowStockAdapter.LowStockViewHolder>() {

    class LowStockViewHolder(val binding: ItemLowStockBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LowStockViewHolder {
        val binding = ItemLowStockBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LowStockViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LowStockViewHolder, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context

        holder.binding.tvLowStockName.text = item.name
        holder.binding.tvLowStockHave.text = item.quantity
        holder.binding.tvLowStockBuy.text = item.suggestedRestockQuantity
            ?: context.getString(R.string.low_stock_no_suggestion)
        holder.binding.tvLowStockDays.text = lowStockDaysText(context, item.daysOfSupply)
    }

    override fun getItemCount(): Int = items.size
}
