package com.project.fridgemate.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Result of toggling follow on a target user. */
data class FollowToggleResponse(
    val following: Boolean,
    val followersCount: Int
)

/** Lightweight user row used in followers/following/search lists. */
data class UserListItemDto(
    @SerializedName(value = "id", alternate = ["_id"]) val id: String,
    val displayName: String,
    val userName: String? = null,
    val profileImage: String? = null,
    val bio: String? = null,
    val address: AddressDto? = null,
    val isFollowing: Boolean = false
)

data class UserListResponse(
    val items: List<UserListItemDto>,
    val total: Int,
    val page: Int,
    val limit: Int
)
