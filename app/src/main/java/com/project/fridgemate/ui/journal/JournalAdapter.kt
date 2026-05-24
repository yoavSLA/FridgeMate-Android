package com.project.fridgemate.ui.journal

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.data.model.JournalEntry
import com.project.fridgemate.databinding.ItemJournalEntryBinding
import com.squareup.picasso.Picasso

class JournalAdapter(private val onItemClick: (JournalEntry) -> Unit) : ListAdapter<JournalEntry, JournalAdapter.JournalViewHolder>(DiffCallback) {

    inner class JournalViewHolder(private val binding: ItemJournalEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(entry: JournalEntry) {
            binding.tvTitle.text = entry.title
            binding.tvContent.text = entry.content
            binding.tvMood.text = entry.mood
            
            val dateStr = DateUtils.getRelativeTimeSpanString(
                entry.dateMillis,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            val mealTypePart = if (entry.mealType.isNotEmpty()) " • ${entry.mealType}" else ""
            binding.tvDate.text = "$dateStr$mealTypePart"

            if (entry.calories.isNotEmpty()) {
                binding.tvCalories.visibility = View.VISIBLE
                binding.tvCalories.text = "${entry.calories} kcal"
            } else {
                binding.tvCalories.visibility = View.GONE
            }

            // Parse macros and show individual badges
            parseMacroBadges(entry.macros)

            if (!entry.imageUrl.isNullOrEmpty()) {
                binding.ivEntryImage.visibility = View.VISIBLE
                Picasso.get()
                    .load(entry.imageUrl)
                    .into(binding.ivEntryImage)
            } else {
                binding.ivEntryImage.visibility = View.GONE
            }
        }

        private fun parseMacroBadges(macros: String) {
            val regexP = Regex("""(\d+)\s*g?\s*P""")
            val regexC = Regex("""(\d+)\s*g?\s*C""")
            val regexF = Regex("""(\d+)\s*g?\s*F""")

            val protein = regexP.find(macros)?.groupValues?.getOrNull(1)
            val carbs = regexC.find(macros)?.groupValues?.getOrNull(1)
            val fat = regexF.find(macros)?.groupValues?.getOrNull(1)

            if (protein != null) {
                binding.tvProtein.visibility = View.VISIBLE
                binding.tvProtein.text = "${protein}g P"
            } else {
                binding.tvProtein.visibility = View.GONE
            }

            if (carbs != null) {
                binding.tvCarbs.visibility = View.VISIBLE
                binding.tvCarbs.text = "${carbs}g C"
            } else {
                binding.tvCarbs.visibility = View.GONE
            }

            if (fat != null) {
                binding.tvFat.visibility = View.VISIBLE
                binding.tvFat.text = "${fat}g F"
            } else {
                binding.tvFat.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val binding = ItemJournalEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return JournalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<JournalEntry>() {
            override fun areItemsTheSame(oldItem: JournalEntry, newItem: JournalEntry): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: JournalEntry, newItem: JournalEntry): Boolean {
                return oldItem == newItem
            }
        }
    }
}
