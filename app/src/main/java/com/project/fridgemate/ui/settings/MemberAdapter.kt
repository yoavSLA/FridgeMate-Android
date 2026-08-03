package com.project.fridgemate.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.data.remote.dto.FridgeMemberDetailDto
import com.project.fridgemate.databinding.ItemMemberBinding
import com.project.fridgemate.utils.AvatarHelper
import com.squareup.picasso.Picasso

class MemberAdapter(
    private val members: List<FridgeMemberDetailDto>,
    private val currentUserId: String? = null,
    private val selectedUserId: String? = null,
    private val onMemberClick: ((FridgeMemberDetailDto) -> Unit)? = null
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    inner class MemberViewHolder(val binding: ItemMemberBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        val isCurrentUser = member.userId == currentUserId
        with(holder.binding) {
            val suffix = when {
                member.userId == selectedUserId -> root.context.getString(R.string.current_owner_suffix)
                isCurrentUser -> root.context.getString(R.string.you_suffix)
                else -> ""
            }
            tvMemberName.text = "${member.displayName}$suffix"
            root.setOnClickListener { onMemberClick?.invoke(member) }
            val profileImage = member.profileImage
            val placeholder = AvatarHelper.createPlaceholder(root.context, member.displayName)
            if (!profileImage.isNullOrEmpty()) {
                val url = if (profileImage.startsWith("/"))
                    BuildConfig.BASE_URL.trimEnd('/') + profileImage
                else profileImage
                Picasso.get()
                    .load(url)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .into(ivMemberPhoto)
            } else {
                ivMemberPhoto.setImageDrawable(placeholder)
            }
        }
    }

    override fun getItemCount() = members.size
}
