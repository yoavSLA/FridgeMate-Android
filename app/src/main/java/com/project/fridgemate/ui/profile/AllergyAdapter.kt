package com.project.fridgemate.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.databinding.ItemAllergyBinding

data class AllergyItem(
    val name: String,
    var isChecked: Boolean = false
)

class AllergyAdapter(
    private val items: List<AllergyItem>,
    private val onToggle: (name: String, isChecked: Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<AllergyAdapter.AllergyViewHolder>() {

    inner class AllergyViewHolder(val binding: ItemAllergyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AllergyViewHolder {
        val binding = ItemAllergyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AllergyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AllergyViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding.cbAllergy) {
            setOnCheckedChangeListener(null)
            text = item.name
            isChecked = item.isChecked

            setOnCheckedChangeListener { _, isChecked ->
                item.isChecked = isChecked
                onToggle(item.name, isChecked)
            }
        }
    }

    override fun getItemCount() = items.size

    fun getSelectedAllergies(): List<String> {
        return items.filter { it.isChecked }.map { it.name }
    }

    fun setSelectedAllergies(selected: List<String>) {
        items.forEach { it.isChecked = selected.contains(it.name) }
        notifyDataSetChanged()
    }
}
