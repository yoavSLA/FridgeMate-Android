package com.project.fridgemate.ui.fridge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.R
import com.project.fridgemate.databinding.ItemCategoryHeaderBinding
import com.project.fridgemate.databinding.ItemProductBinding
import com.project.fridgemate.databinding.ItemRunningLowBinding

class FridgeAdapter(private val items: List<FridgeItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_LAST_SCANNED = -1
        private const val TYPE_RUNNING_LOW = 0
        private const val TYPE_CATEGORY_HEADER = 1
        private const val TYPE_PRODUCT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FridgeItem.LastScanned -> TYPE_LAST_SCANNED
            is FridgeItem.RunningLow -> TYPE_RUNNING_LOW
            is FridgeItem.CategoryHeader -> TYPE_CATEGORY_HEADER
            is FridgeItem.Product -> TYPE_PRODUCT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_LAST_SCANNED -> {
                val view = inflater.inflate(R.layout.item_last_scanned, parent, false)
                LastScannedViewHolder(view)
            }
            TYPE_RUNNING_LOW -> {
                val binding = ItemRunningLowBinding.inflate(inflater, parent, false)
                RunningLowViewHolder(binding)
            }
            TYPE_CATEGORY_HEADER -> {
                val binding = ItemCategoryHeaderBinding.inflate(inflater, parent, false)
                CategoryHeaderViewHolder(binding)
            }
            TYPE_PRODUCT -> {
                val binding = ItemProductBinding.inflate(inflater, parent, false)
                ProductViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FridgeItem.LastScanned -> (holder as LastScannedViewHolder).bind(item)
            is FridgeItem.RunningLow -> (holder as RunningLowViewHolder).bind(item)
            is FridgeItem.CategoryHeader -> (holder as CategoryHeaderViewHolder).bind(item)
            is FridgeItem.Product -> {
                val isFirstInGroup = position == 0 || items[position - 1] !is FridgeItem.Product
                val isLastInGroup = position == items.size - 1 || items[position + 1] !is FridgeItem.Product
                (holder as ProductViewHolder).bind(item, isFirstInGroup, isLastInGroup)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class LastScannedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTime: android.widget.TextView = view.findViewById(R.id.tvLastScannedTime)
        fun bind(item: FridgeItem.LastScanned) {
            tvTime.text = item.timestamp
        }
    }

    class RunningLowViewHolder(private val binding: ItemRunningLowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FridgeItem.RunningLow) {
            val listString = item.ingredients.joinToString(", ") { (name, qty) -> "$name ($qty)" }
            binding.tvLowStockList.text = binding.root.context.getString(R.string.low_stock_restock_format, listString)
        }
    }

    class CategoryHeaderViewHolder(private val binding: ItemCategoryHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FridgeItem.CategoryHeader) {
            binding.tvCategoryName.text = item.name.uppercase()
        }
    }

    class ProductViewHolder(private val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FridgeItem.Product, isFirstInGroup: Boolean, isLastInGroup: Boolean) {
            binding.tvProductName.text = item.name
            binding.tvProductQuantity.text = item.quantity
            binding.ivLowStockWarning.visibility = if (item.isLowStock) View.VISIBLE else View.GONE
            binding.divider.visibility = if (isLastInGroup) View.GONE else View.VISIBLE
            
            val context = binding.root.context
            if (isFirstInGroup && isLastInGroup) {
                // Single item in group - rounded top and bottom
                binding.root.setBackgroundResource(R.drawable.bg_product_item_all)
            } else if (isFirstInGroup) {
                // First in group - rounded top
                binding.root.setBackgroundResource(R.drawable.bg_product_item_top)
            } else if (isLastInGroup) {
                // Last in group - rounded bottom
                binding.root.setBackgroundResource(R.drawable.bg_product_item)
            } else {
                // Middle item - no rounding
                binding.root.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
            }
        }
    }
}
