package com.project.fridgemate.ui.fridge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.project.fridgemate.R
import com.project.fridgemate.data.remote.dto.FridgeMemberDetailDto
import com.project.fridgemate.databinding.ItemCategoryHeaderBinding
import com.project.fridgemate.databinding.ItemProductBinding
import com.project.fridgemate.databinding.ItemRunningLowBinding

class FridgeAdapter(
    private var items: List<FridgeItem>,
    private var members: Map<String, FridgeMemberDetailDto> = emptyMap(),
    private val onOwnerIconClick: (View, FridgeItem.Product) -> Unit = { _, _ -> },
    private val onOwnerRemoveClick: (FridgeItem.Product) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun updateItems(newItems: List<FridgeItem>, newMembers: Map<String, FridgeMemberDetailDto>) {
        val diffCallback = FridgeDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        this.items = newItems
        this.members = newMembers
        diffResult.dispatchUpdatesTo(this)
    }

    private class FridgeDiffCallback(
        private val oldList: List<FridgeItem>,
        private val newList: List<FridgeItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return when {
                oldItem is FridgeItem.Product && newItem is FridgeItem.Product -> oldItem.id == newItem.id
                oldItem is FridgeItem.CategoryHeader && newItem is FridgeItem.CategoryHeader -> oldItem.name == newItem.name
                oldItem is FridgeItem.RunningLow && newItem is FridgeItem.RunningLow -> true
                else -> false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    companion object {
        private const val TYPE_RUNNING_LOW = 0
        private const val TYPE_CATEGORY_HEADER = 1
        private const val TYPE_PRODUCT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FridgeItem.RunningLow -> TYPE_RUNNING_LOW
            is FridgeItem.CategoryHeader -> TYPE_CATEGORY_HEADER
            is FridgeItem.Product -> TYPE_PRODUCT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
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
            is FridgeItem.RunningLow -> (holder as RunningLowViewHolder).bind(item)
            is FridgeItem.CategoryHeader -> {
                val colors = getCategoryColors(item.name)
                val isFirstCategory = items.indexOfFirst { it is FridgeItem.CategoryHeader } == position
                (holder as CategoryHeaderViewHolder).bind(item, colors, isFirstCategory)
            }
            is FridgeItem.Product -> {
                val isFirstInGroup = position == 0 || items[position - 1] !is FridgeItem.Product
                val isLastInGroup = position == items.size - 1 || items[position + 1] !is FridgeItem.Product
                val colors = getCategoryColors(item.category ?: "Other")
                (holder as ProductViewHolder).bind(
                    item, isFirstInGroup, isLastInGroup, colors, members[item.ownerId], onOwnerIconClick, onOwnerRemoveClick
                )
            }
        }
    }

    private val categoryColorMap = mutableMapOf<String, CategoryColors>()
    
    data class CategoryColors(val backgroundRes: Int, val accentRes: Int)

    private val hueList = listOf(
        CategoryColors(R.color.chat_bubble_user_6, R.color.chat_sender_user_6),  // Dark Blue
        CategoryColors(R.color.chat_bubble_user_10, R.color.chat_sender_user_10), // Dark Green
        CategoryColors(R.color.chat_bubble_user_1, R.color.chat_sender_user_1),  // Red
        CategoryColors(R.color.chat_bubble_user_14, R.color.chat_sender_user_14), // Deep Orange
        CategoryColors(R.color.chat_bubble_user_3, R.color.chat_sender_user_3),  // Purple
        CategoryColors(R.color.chat_bubble_user_8, R.color.chat_sender_user_8),  // Teal
        CategoryColors(R.color.chat_bubble_user_13, R.color.chat_sender_user_13), // Amber/Yellow
        CategoryColors(R.color.chat_bubble_user_5, R.color.chat_sender_user_5),  // Indigo
        CategoryColors(R.color.chat_bubble_user_15, R.color.chat_sender_user_15), // Burnt Orange
        CategoryColors(R.color.chat_bubble_user_2, R.color.chat_sender_user_2),  // Deep Pink
        CategoryColors(R.color.chat_bubble_user_11, R.color.chat_sender_user_11), // Lime Green
        CategoryColors(R.color.chat_bubble_user_7, R.color.chat_sender_user_7),  // Sky Blue
        CategoryColors(R.color.chat_bubble_user_4, R.color.chat_sender_user_4),  // Deep Lavender
        CategoryColors(R.color.chat_bubble_user_12, R.color.chat_sender_user_12), // Olive
        CategoryColors(R.color.chat_bubble_user_9, R.color.chat_sender_user_9)   // Dark Teal
    )

    private fun getCategoryColors(category: String): CategoryColors {
        val normalized = category.lowercase().trim()
        return categoryColorMap.getOrPut(normalized) {
            hueList[categoryColorMap.size % hueList.size]
        }
    }

    override fun getItemCount(): Int = items.size

    class RunningLowViewHolder(private val binding: ItemRunningLowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FridgeItem.RunningLow) {
            val listString = item.ingredients.joinToString(", ") { (name, qty) -> "$name ($qty)" }
            binding.tvLowStockList.text = binding.root.context.getString(R.string.low_stock_restock_format, listString)
        }
    }

    class CategoryHeaderViewHolder(private val binding: ItemCategoryHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FridgeItem.CategoryHeader, colors: CategoryColors, isFirst: Boolean) {
            binding.tvCategoryName.text = item.name.uppercase()
            val accentColor = ContextCompat.getColor(binding.root.context, colors.accentRes)
            binding.vCategoryIndicator.setBackgroundColor(accentColor)
            binding.tvCategoryName.setTextColor(accentColor)

            val params = binding.root.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = if (isFirst) {
                (binding.root.context.resources.displayMetrics.density * 8).toInt()
            } else {
                (binding.root.context.resources.displayMetrics.density * 32).toInt()
            }
            binding.root.layoutParams = params
        }
    }

    class ProductViewHolder(private val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: FridgeItem.Product,
            isFirstInGroup: Boolean,
            isLastInGroup: Boolean,
            colors: CategoryColors,
            owner: FridgeMemberDetailDto?,
            onOwnerIconClick: (View, FridgeItem.Product) -> Unit,
            onOwnerRemoveClick: (FridgeItem.Product) -> Unit
        ) {
            val context = binding.root.context
            binding.tvProductName.text = item.name
            binding.tvProductQuantity.text = item.quantity
            binding.ivLowStockWarning.visibility = if (item.isLowStock) View.VISIBLE else View.GONE

            val accentColor = ContextCompat.getColor(context, colors.accentRes)
            
            binding.tvProductQuantity.setTextColor(ContextCompat.getColor(context, R.color.white))
            binding.tvProductQuantity.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(accentColor, 135)
            )

            val isAssigned = item.ownerId != null
            if (isAssigned) {
                binding.ownerContainer.setBackgroundResource(R.drawable.bg_owner_pill)
                // Owner Badge: Keep it subtle with a tint and colored text
                binding.ownerContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(accentColor, 32)
                )
                binding.tvOwnerName.text = owner?.displayName
                    ?: context.getString(R.string.unassigned_owner)
                binding.tvOwnerName.visibility = View.VISIBLE
                binding.ivOwnerRemove.visibility = View.VISIBLE
                binding.tvOwnerName.setTextColor(accentColor)
                binding.ivOwnerIcon.setColorFilter(accentColor)
                binding.ivOwnerRemove.setColorFilter(accentColor)
            } else {
                binding.ownerContainer.background = null
                binding.tvOwnerName.visibility = View.GONE
                binding.ivOwnerRemove.visibility = View.GONE
                binding.ivOwnerIcon.setColorFilter(ContextCompat.getColor(context, R.color.gray_text))
            }
            binding.ownerContainer.setOnClickListener { onOwnerIconClick(binding.ownerContainer, item) }
            binding.ivOwnerRemove.setOnClickListener { onOwnerRemoveClick(item) }

            val rootDrawable = ContextCompat.getDrawable(context, when {
                isFirstInGroup && isLastInGroup -> R.drawable.bg_product_item_all
                isFirstInGroup -> R.drawable.bg_product_item_top
                isLastInGroup -> R.drawable.bg_product_item
                else -> R.drawable.bg_product_item_middle
            })?.mutate()

            val bgDrawable = if (rootDrawable is android.graphics.drawable.LayerDrawable) {
                rootDrawable.getDrawable(0) as? android.graphics.drawable.GradientDrawable
            } else {
                rootDrawable as? android.graphics.drawable.GradientDrawable
            }

            bgDrawable?.let {
                val strokeWidth = (context.resources.displayMetrics.density * 0.8f).toInt().coerceAtLeast(1)
                val strokeColor = ContextCompat.getColor(context, R.color.card_stroke_color)
                it.setStroke(strokeWidth, strokeColor)
                binding.root.background = rootDrawable
            }
            
            binding.root.elevation = 2f * context.resources.displayMetrics.density
            binding.root.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            
            binding.divider.visibility = if (isLastInGroup) View.GONE else View.VISIBLE
            binding.divider.setBackgroundColor(ContextCompat.getColor(context, R.color.divider_color))

            binding.root.backgroundTintList = null
        }
    }
}
