package com.project.fridgemate.ui.journal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.data.model.JournalEntry
import com.project.fridgemate.databinding.ItemJournalDayGroupBinding
import com.project.fridgemate.databinding.ItemJournalEntryRowBinding
import com.squareup.picasso.Picasso

import java.util.Locale

data class JournalDayGroup(
    val id: String,
    val dateLabel: String,
    val entries: List<JournalEntry>,
    val totalCalories: Int,
    val totalProtein: Int,
    val totalCarbs: Int,
    val totalFat: Int
)

class JournalAdapter(
    private val onItemClick: (JournalEntry) -> Unit,
    private val onRecipeImageClick: (String) -> Unit = {}
) : ListAdapter<JournalDayGroup, JournalAdapter.JournalViewHolder>(DiffCallback) {

    inner class JournalViewHolder(private val binding: ItemJournalDayGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(group: JournalDayGroup) {
            binding.tvDateHeader.text = group.dateLabel
            binding.tvEntryCount.text = binding.root.context.resources.getQuantityString(
                R.plurals.journal_entries_count,
                group.entries.size,
                group.entries.size
            )
            
            binding.tvTotalCalories.text = String.format(Locale.getDefault(), "%,d", group.totalCalories)
            binding.tvTotalProtein.text = binding.root.context.getString(R.string.journal_macro_suffix_format, group.totalProtein)
            binding.tvTotalCarbs.text = binding.root.context.getString(R.string.journal_macro_suffix_format, group.totalCarbs)
            binding.tvTotalFat.text = binding.root.context.getString(R.string.journal_macro_suffix_format, group.totalFat)

            binding.entriesContainer.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)

            group.entries.forEach { entry ->
                val rowBinding = ItemJournalEntryRowBinding.inflate(inflater, binding.entriesContainer, false)
                
                rowBinding.tvTitle.text = entry.title
                rowBinding.tvContent.text = entry.content
                rowBinding.tvContent.visibility = if (entry.content.isEmpty()) View.GONE else View.VISIBLE
                
                rowBinding.tvMood.text = entry.mood
                rowBinding.tvMood.visibility = if (entry.mood.isEmpty()) View.GONE else View.VISIBLE
                
                rowBinding.tvMealType.text = entry.mealType
                rowBinding.tvMealType.visibility = if (entry.mealType.isEmpty()) View.GONE else View.VISIBLE

                if (entry.calories.isNotEmpty()) {
                    rowBinding.tvCalories.visibility = View.VISIBLE
                    val calVal = entry.calories.replace(Regex("\\D"), "")
                    rowBinding.tvCalories.text = rowBinding.root.context.getString(R.string.kcal_format, calVal)
                } else {
                    rowBinding.tvCalories.visibility = View.GONE
                }

                rowBinding.tvMacros.text = entry.macros
                rowBinding.tvMacros.visibility = if (entry.macros.isEmpty()) View.GONE else View.VISIBLE

                if (!entry.imageUrl.isNullOrEmpty()) {
                    rowBinding.ivEntryImage.visibility = View.VISIBLE
                    val fullUrl = if (entry.imageUrl.startsWith("/")) {
                        BuildConfig.BASE_URL.trimEnd('/') + entry.imageUrl
                    } else {
                        entry.imageUrl
                    }
                    Picasso.get()
                        .load(fullUrl)
                        .fit()
                        .centerCrop()
                        .into(rowBinding.ivEntryImage)
                    
                    if (entry.recipeId != null) {
                        rowBinding.ivEntryImage.setOnClickListener {
                            onRecipeImageClick(entry.recipeId)
                        }
                    } else {
                        rowBinding.ivEntryImage.setOnClickListener(null)
                    }
                } else {
                    rowBinding.ivEntryImage.visibility = View.GONE
                }

                rowBinding.root.setOnClickListener { onItemClick(entry) }
                binding.entriesContainer.addView(rowBinding.root)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val binding = ItemJournalDayGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return JournalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<JournalDayGroup>() {
            override fun areItemsTheSame(oldItem: JournalDayGroup, newItem: JournalDayGroup): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: JournalDayGroup, newItem: JournalDayGroup): Boolean {
                return oldItem == newItem
            }
        }
    }
}
