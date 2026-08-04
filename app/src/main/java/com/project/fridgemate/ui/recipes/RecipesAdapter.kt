package com.project.fridgemate.ui.recipes

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.data.local.entity.RecipeEntity
import com.project.fridgemate.databinding.ItemRecipeBinding
import com.squareup.picasso.Picasso

class RecipeAdapter(
    private val onFavoriteClick: (RecipeEntity) -> Unit,
    private val onAddToJournalClick: (RecipeEntity) -> Unit = {},
    private val onItemClick: (RecipeEntity) -> Unit = {}
) : ListAdapter<RecipeEntity, RecipeAdapter.RecipeViewHolder>(DIFF) {

    companion object {
        private const val PAYLOAD_FAVORITE = "PAYLOAD_FAVORITE"

        private val DIFF = object : DiffUtil.ItemCallback<RecipeEntity>() {
            override fun areItemsTheSame(a: RecipeEntity, b: RecipeEntity) = a.id == b.id
            
            override fun areContentsTheSame(a: RecipeEntity, b: RecipeEntity) = a == b

            override fun getChangePayload(oldItem: RecipeEntity, newItem: RecipeEntity): Any? {
                return if (oldItem.isFavorite != newItem.isFavorite) {
                    PAYLOAD_FAVORITE
                } else {
                    super.getChangePayload(oldItem, newItem)
                }
            }
        }
    }

    class RecipeViewHolder(val binding: ItemRecipeBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = getItem(position)
        with(holder.binding) {
            tvRecipeName.text = recipe.title
            tvRecipeTime.text = recipe.cookingTime
            tvRecipeDifficulty.text = recipe.difficulty
            tvCalories.text = recipe.calories.replace(Regex("\\D"), "").ifEmpty { "-" }
            tvFat.text = recipe.fat.ifEmpty { "-" }
            tvCarbs.text = recipe.carbs.ifEmpty { "-" }
            tvProtein.text = recipe.protein.ifEmpty { "-" }

            if (recipe.imageUrl.isNotBlank()) {
                val fullUrl = if (recipe.imageUrl.startsWith("/")) {
                    BuildConfig.BASE_URL.trimEnd('/') + recipe.imageUrl
                } else {
                    recipe.imageUrl
                }
                Picasso.get()
                    .load(fullUrl)
                    .fit()
                    .centerCrop()
                    .placeholder(R.color.accent_green_light)
                    .error(R.color.accent_green_light)
                    .into(ivRecipeImage)
            } else {
                ivRecipeImage.setImageResource(R.color.accent_green_light)
            }

            updateFavoriteIcon(btnFavorite, recipe.isFavorite, animate = false)
            
            btnFavorite.setOnClickListener { 
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onFavoriteClick(getItem(currentPos))
                }
            }
            btnAddToJournal.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onAddToJournalClick(getItem(currentPos))
                }
            }
            root.setOnClickListener { onItemClick(recipe) }
        }
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_FAVORITE)) {
            val recipe = getItem(position)
            updateFavoriteIcon(holder.binding.btnFavorite, recipe.isFavorite, animate = true)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun updateFavoriteIcon(btn: ImageButton, isFavorite: Boolean, animate: Boolean) {
        if (isFavorite) {
            btn.setImageResource(R.drawable.ic_star_filled)
            btn.imageTintList = ColorStateList.valueOf(btn.context.getColor(R.color.favorite_gold))
            if (animate) animateStar(btn)
        } else {
            btn.setImageResource(R.drawable.ic_star_outline)
            btn.imageTintList = ColorStateList.valueOf(btn.context.getColor(R.color.gray_text))
            if (animate) {
                viewScalePop(btn, 0.9f)
            }
        }
    }

    private fun animateStar(view: ImageButton) {
        viewScalePop(view, 1.2f)
    }

    private fun viewScalePop(view: ImageButton, scale: Float) {
        view.animate().cancel()
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }
}
