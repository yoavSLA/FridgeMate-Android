package com.project.fridgemate.ui.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.project.fridgemate.databinding.DialogCommentOptionsBinding
import com.project.fridgemate.databinding.DialogConfirmDeleteBinding
import com.project.fridgemate.databinding.ItemCommentBinding
import com.project.fridgemate.utils.TimeAgo
import com.squareup.picasso.Picasso

class CommentAdapter(
    private val onDeleteComment: (Comment) -> Unit = {},
    private val onEditComment: (Comment, String) -> Unit = { _, _ -> },
    private val showOptions: Boolean = true
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private var comments: List<Comment> = emptyList()
    private var editingCommentId: String? = null

    fun submitList(newList: List<Comment>) {
        if (this.comments == newList) return
        this.comments = newList
        notifyDataSetChanged()
    }

    inner class CommentViewHolder(val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        with(holder.binding) {
            tvCommentUserName.text = comment.userName
            tvCommentText.text = comment.text
            val timeAgo = TimeAgo.format(comment.createdAt)
            tvCommentTime.text = timeAgo
            tvCommentTime.visibility = if (timeAgo.isEmpty()) View.GONE else View.VISIBLE
            
            if (editingCommentId == comment.id) {
                tvCommentText.visibility = View.GONE
                layoutEditComment.visibility = View.VISIBLE
                btnCommentOptions.visibility = View.GONE
                etEditComment.setText(comment.text)
                etEditComment.requestFocus()
                
                btnSaveEdit.setOnClickListener {
                    val newText = etEditComment.text.toString().trim()
                    if (newText.isNotEmpty()) {
                        onEditComment(comment, newText)
                        editingCommentId = null
                        notifyItemChanged(position)
                    }
                }
                
                btnCancelEdit.setOnClickListener {
                    editingCommentId = null
                    notifyItemChanged(position)
                }
            } else {
                tvCommentText.visibility = View.VISIBLE
                layoutEditComment.visibility = View.GONE
                if (showOptions && comment.isOwner) {
                    btnCommentOptions.visibility = View.VISIBLE
                    btnCommentOptions.setOnClickListener {
                        showCommentOptions(it, comment)
                    }
                } else {
                    btnCommentOptions.visibility = View.GONE
                }
            }

            if (comment.authorImageUrl.isNotEmpty()) {
                val url = if (comment.authorImageUrl.startsWith("/"))
                    BuildConfig.BASE_URL.trimEnd('/') + comment.authorImageUrl
                else comment.authorImageUrl
                Picasso.get()
                    .load(url)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .noFade()
                    .into(ivCommentUserPhoto)
            } else {
                ivCommentUserPhoto.setImageResource(R.drawable.ic_person)
            }
        }
    }

    private fun showCommentOptions(anchor: View, comment: Comment) {
        val context = anchor.context
        val dialog = BottomSheetDialog(context)
        val binding = DialogCommentOptionsBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        binding.btnEdit.setOnClickListener {
            dialog.dismiss()
            editingCommentId = comment.id
            val pos = comments.indexOfFirst { it.id == comment.id }
            if (pos != -1) notifyItemChanged(pos)
        }

        binding.btnDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmation(context, comment)
        }

        dialog.show()
    }

    private fun showDeleteConfirmation(context: android.content.Context, comment: Comment) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        binding.tvTitle.text = context.getString(R.string.delete_comment)
        binding.tvMessage.text = context.getString(R.string.delete_comment_confirmation)

        binding.btnConfirmDelete.setOnClickListener {
            onDeleteComment(comment)
            dialog.dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun getItemCount() = comments.size
}
