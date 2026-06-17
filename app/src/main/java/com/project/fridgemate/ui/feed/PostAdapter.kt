package com.project.fridgemate.ui.feed

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.databinding.DialogConfirmDeleteBinding
import com.project.fridgemate.databinding.DialogPostOptionsBinding
import com.project.fridgemate.databinding.ItemPostBinding
import com.squareup.picasso.Picasso

class PostAdapter(
    private val onLikeClick: (Post) -> Unit,
    private val onAddComment: (postId: String, text: String) -> Unit,
    private val onDeleteClick: (Post) -> Unit,
    private val onEditClick: (Post) -> Unit,
    private val onDeleteComment: (postId: String, commentId: String) -> Unit,
    private val onEditComment: (postId: String, commentId: String, newText: String) -> Unit,
    private val onExpandComments: (String) -> Unit,
    private val onRecipeClick: (LinkedRecipe) -> Unit = {},
    private val onLocationClick: (Post) -> Unit = {},
    private val onAuthorClick: (Post) -> Unit = {}
) : ListAdapter<Post, PostAdapter.PostViewHolder>(DIFF_CALLBACK) {

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

    inner class PostViewHolder(val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = getItem(position)
        with(holder.binding) {

            tvUserName.text = post.userName
            tvUserLocation.text = post.userLocation
            if (post.authorId.isNotEmpty()) {
                val authorClickListener = View.OnClickListener { onAuthorClick(post) }
                ivUserPhoto.setOnClickListener(authorClickListener)
                tvUserName.setOnClickListener(authorClickListener)
            } else {
                ivUserPhoto.setOnClickListener(null)
                tvUserName.setOnClickListener(null)
            }
            val hasCoords = post.latitude != 0.0 || post.longitude != 0.0
            if (hasCoords && post.userLocation.isNotBlank()) {
                tvUserLocation.setOnClickListener { onLocationClick(post) }
                tvUserLocation.alpha = 1f
                tvUserLocation.paintFlags = tvUserLocation.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            } else {
                tvUserLocation.setOnClickListener(null)
                tvUserLocation.paintFlags = tvUserLocation.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
            }
            if (post.authorImageUrl.isNotEmpty()) {
                val url = if (post.authorImageUrl.startsWith("/"))
                    BuildConfig.BASE_URL.trimEnd('/') + post.authorImageUrl
                else post.authorImageUrl
                Picasso.get()
                    .load(url)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(ivUserPhoto)
            } else {
                ivUserPhoto.setImageResource(R.drawable.ic_person)
            }

            if (post.imageUrl.isNotEmpty()) {
                ivPostImage.visibility = View.VISIBLE
                ivPostImage.setBackgroundColor(0)
                val url = if (post.imageUrl.startsWith("/"))
                    BuildConfig.BASE_URL.trimEnd('/') + post.imageUrl
                else post.imageUrl
                Picasso.get()
                    .load(url)
                    .placeholder(R.color.light_teal)
                    .error(R.color.light_teal)
                    .into(ivPostImage)
            } else {
                ivPostImage.visibility = View.GONE
            }

            tvRecipeTitle.text = post.postTitle
            tvDescription.text = post.description

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
                    val url = if (recipe.imageUrl.startsWith("/"))
                        BuildConfig.BASE_URL.trimEnd('/') + recipe.imageUrl
                    else recipe.imageUrl
                    Picasso.get()
                        .load(url)
                        .placeholder(R.color.light_teal)
                        .error(R.color.light_teal)
                        .into(ivRecipeThumb)
                } else {
                    ivRecipeThumb.setImageResource(R.color.teal_primary)
                }
                cardLinkedRecipe.setOnClickListener { onRecipeClick(recipe) }
            } else {
                cardLinkedRecipe.visibility = View.GONE
            }

            tvLikesCount.text = post.likesCount.toString()
            updateLikeButton(btnLike, post.isLiked, animate = false)

            tvCommentsCount.text = post.commentsCount.toString()

            btnLike.setOnClickListener { 
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onLikeClick(getItem(currentPos))
                }
            }
            
            btnComment.setOnClickListener {
                onExpandComments(post.id)
            }

            if (post.isExpanded) {
                setupComments(holder, post)
                layoutCommentsSection.visibility = View.VISIBLE
            } else {
                layoutCommentsSection.visibility = View.GONE
            }

            btnSendComment.setOnClickListener {
                val text = etComment.text.toString().trim()
                if (text.isNotEmpty()) {
                    onAddComment(post.id, text)
                    etComment.text?.clear()
                }
            }
            if (post.isOwner) {
                btnMoreOptions.visibility = View.VISIBLE
                btnMoreOptions.setOnClickListener {
                    showOptionsMenu(it, post)
                }
            } else {
                btnMoreOptions.visibility = View.GONE
            }
        }
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_LIKE)) {
            val post = getItem(position)
            holder.binding.tvLikesCount.text = post.likesCount.toString()
            updateLikeButton(holder.binding.btnLike, post.isLiked, animate = true)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun setupComments(holder: PostViewHolder, post: Post) {
        val rv = holder.binding.rvComments
        
        // Use the existing adapter if possible to avoid flickering and unnecessary layout passes
        var adapter = rv.adapter as? CommentAdapter
        if (adapter == null) {
            rv.itemAnimator = null // No animations for nested recycler
            rv.layoutManager = LinearLayoutManager(holder.itemView.context)
            adapter = CommentAdapter(
                onDeleteComment = { comment -> onDeleteComment(post.id, comment.id) },
                onEditComment = { comment, newText -> onEditComment(post.id, comment.id, newText) }
            )
            rv.adapter = adapter
        }
        
        adapter.submitList(post.comments)
    }

    private fun showOptionsMenu(anchor: View, post: Post) {
        val context = anchor.context
        val dialog = BottomSheetDialog(context)
        val binding = DialogPostOptionsBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        binding.btnEdit.setOnClickListener {
            onEditClick(post)
            dialog.dismiss()
        }

        binding.btnDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmation(context, post)
        }

        dialog.show()
    }

    private fun showDeleteConfirmation(context: android.content.Context, post: Post) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        binding.tvTitle.text = context.getString(R.string.delete_post)
        binding.tvMessage.text = context.getString(R.string.delete_post_confirmation)

        binding.btnConfirmDelete.setOnClickListener {
            onDeleteClick(post)
            dialog.dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
