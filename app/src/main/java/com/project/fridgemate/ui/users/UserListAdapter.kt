package com.project.fridgemate.ui.users

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.data.remote.dto.UserListItemDto
import com.project.fridgemate.databinding.ItemUserRowBinding
import com.squareup.picasso.Picasso

class UserListAdapter(
    private val currentUserId: String?,
    private val onUserClick: (UserListItemDto) -> Unit,
    private val onFollowClick: (UserListItemDto) -> Unit
) : ListAdapter<UserListItemDto, UserListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserListItemDto>() {
            override fun areItemsTheSame(a: UserListItemDto, b: UserListItemDto) = a.id == b.id
            override fun areContentsTheSame(a: UserListItemDto, b: UserListItemDto) = a == b
        }
    }

    inner class VH(val binding: ItemUserRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemUserRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = getItem(position)
        with(holder.binding) {
            tvDisplayName.text = user.displayName
            tvUserName.text = user.userName?.let { "@$it" } ?: ""
            tvUserName.visibility = if (user.userName.isNullOrEmpty()) View.GONE else View.VISIBLE

            val location = listOfNotNull(
                user.address?.city?.takeIf { it.isNotEmpty() },
                user.address?.country?.takeIf { it.isNotEmpty() }
            ).joinToString(", ")
            tvSubtitle.text = when {
                !user.bio.isNullOrBlank() -> user.bio
                location.isNotEmpty() -> location
                else -> ""
            }
            tvSubtitle.visibility = if (tvSubtitle.text.isNullOrEmpty()) View.GONE else View.VISIBLE

            val avatarUrl = user.profileImage
            if (!avatarUrl.isNullOrEmpty()) {
                val full = if (avatarUrl.startsWith("/")) BuildConfig.BASE_URL.trimEnd('/') + avatarUrl else avatarUrl
                Picasso.get()
                    .load(full)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(ivAvatar)
            } else {
                ivAvatar.setImageResource(R.drawable.ic_person)
            }

            val isSelf = currentUserId != null && user.id == currentUserId
            if (isSelf) {
                btnFollow.visibility = View.GONE
            } else {
                btnFollow.visibility = View.VISIBLE
                btnFollow.setText(
                    if (user.isFollowing) R.string.following_action else R.string.follow_action
                )
                btnFollow.setBackgroundColor(
                    root.context.getColor(
                        if (user.isFollowing) R.color.gray_text else R.color.teal_primary
                    )
                )
                btnFollow.setOnClickListener { onFollowClick(user) }
            }

            root.setOnClickListener { onUserClick(user) }
        }
    }
}
