package com.project.fridgemate.ui.fridge

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.data.remote.dto.FridgeMemberDetailDto
import com.project.fridgemate.databinding.ItemMemberDropdownBinding
import com.project.fridgemate.utils.AvatarHelper
import com.squareup.picasso.Picasso

class MemberDropdownAdapter(
    private val members: List<FridgeMemberDetailDto>,
    private val selectedUserId: String? = null,
    private val onMemberClick: (FridgeMemberDetailDto) -> Unit
) : RecyclerView.Adapter<MemberDropdownAdapter.MemberViewHolder>() {

    class MemberViewHolder(val binding: ItemMemberDropdownBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberDropdownBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        with(holder.binding) {
            val suffix = if (member.userId == selectedUserId) " (Owner)" else ""
            tvMemberName.text = "${member.displayName}$suffix"
            root.setOnClickListener { onMemberClick(member) }
            
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
