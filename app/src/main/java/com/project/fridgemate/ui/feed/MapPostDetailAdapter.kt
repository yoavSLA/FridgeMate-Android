package com.project.fridgemate.ui.feed

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.databinding.ItemMapPostDetailBinding
import com.squareup.picasso.Picasso

class MapPostDetailAdapter(
    private val onRecipeClick: (LinkedRecipe) -> Unit,
    private val onLikeClick: (Post) -> Unit,
    private val onCommentClick: (Post) -> Unit
) : ListAdapter<Post, MapPostDetailAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val PAYLOAD_LIKE = "PAYLOAD_LIKE"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Post>() {
            override fun areItemsTheSame(oldItem: Post, newItem: Post) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Post, newItem: Post) = oldItem == newItem

            override fun getChangePayload(oldItem: Post, newItem: Post): Any? {
                return if (oldItem.isLiked != newItem.isLiked || oldItem.likesCount != newItem.likesCount) {
                    PAYLOAD_LIKE
                } else {
                    null
                }
            }
        }
    }

    class ViewHolder(val binding: ItemMapPostDetailBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMapPostDetailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = getItem(position)
        with(holder.binding) {
            tvUserName.text = post.userName
            tvLocation.text = post.userLocation
            tvPostTitle.text = post.postTitle
            tvDescription.text = post.description
            tvLikesCount.text = post.likesCount.toString()
            tvCommentsCount.text = post.commentsCount.toString()

            updateLikeButton(btnLike, post.isLiked, animate = false)

            if (post.authorImageUrl.isNotEmpty()) {
                val avatarUrl = if (post.authorImageUrl.startsWith("/"))
                    BuildConfig.BASE_URL.trimEnd('/') + post.authorImageUrl
                else post.authorImageUrl
                Picasso.get()
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(ivUserAvatar)
            } else {
                ivUserAvatar.setImageResource(R.drawable.ic_person)
            }

            if (post.imageUrl.isNotEmpty()) {
                ivPostImage.visibility = View.VISIBLE
                val fullUrl = if (post.imageUrl.startsWith("/")) {
                    BuildConfig.BASE_URL.trimEnd('/') + post.imageUrl
                } else {
                    post.imageUrl
                }
                Picasso.get()
                    .load(fullUrl)
                    .placeholder(R.color.light_teal)
                    .error(R.color.light_teal)
                    .into(ivPostImage)
            } else {
                ivPostImage.visibility = View.GONE
            }

            val recipe = post.linkedRecipe
            if (recipe != null) {
                cardLinkedRecipe.visibility = View.VISIBLE
                tvLinkedRecipeTitle.text = recipe.title
                
                tvCookingTime.text = recipe.cookingTime
                tvDifficulty.text = recipe.difficulty

                // Hide icons if info is missing
                ivTimeIcon.visibility = if (recipe.cookingTime.isBlank()) View.GONE else View.VISIBLE
                tvCookingTime.visibility = if (recipe.cookingTime.isBlank()) View.GONE else View.VISIBLE
                ivDifficultyIcon.visibility = if (recipe.difficulty.isBlank()) View.GONE else View.VISIBLE
                tvDifficulty.visibility = if (recipe.difficulty.isBlank()) View.GONE else View.VISIBLE

                if (recipe.imageUrl.isNotEmpty()) {
                    val fullRecipeUrl = if (recipe.imageUrl.startsWith("/")) {
                        BuildConfig.BASE_URL.trimEnd('/') + recipe.imageUrl
                    } else {
                        recipe.imageUrl
                    }
                    Picasso.get()
                        .load(fullRecipeUrl)
                        .placeholder(R.color.teal_primary)
                        .into(ivRecipeThumb)
                } else {
                    ivRecipeThumb.setImageResource(R.color.teal_primary)
                }

                cardLinkedRecipe.setOnClickListener {
                    onRecipeClick(recipe)
                }
            } else {
                cardLinkedRecipe.visibility = View.GONE
            }

            btnLike.setOnClickListener {
                onLikeClick(getItem(holder.adapterPosition))
            }

            btnComment.setOnClickListener {
                onCommentClick(getItem(holder.adapterPosition))
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_LIKE)) {
            val post = getItem(position)
            holder.binding.tvLikesCount.text = post.likesCount.toString()
            updateLikeButton(holder.binding.btnLike, post.isLiked, animate = true)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun updateLikeButton(btn: ImageButton, isLiked: Boolean, animate: Boolean) {
        if (isLiked) {
            btn.setImageResource(R.drawable.ic_heart_filled)
            btn.imageTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            if (animate) viewScalePop(btn, 1.2f)
        } else {
            btn.setImageResource(R.drawable.ic_heart_outline)
            btn.imageTintList = ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
            if (animate) viewScalePop(btn, 0.9f)
        }
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
