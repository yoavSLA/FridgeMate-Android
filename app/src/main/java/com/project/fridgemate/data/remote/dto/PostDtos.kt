package com.project.fridgemate.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Author (populated from authorUserId) ────────────────────────────────────

data class PostAuthorDto(
    @SerializedName("_id") val id: String,
    val displayName: String,
    val profileImage: String?,
    val address: AddressDto?
)

// ── Post ────────────────────────────────────────────────────────────────────

data class PostDto(
    @SerializedName("_id") val id: String,
    val authorUserId: PostAuthorDto,
    val title: String?,
    val text: String,
    val mediaUrls: List<String>,
    val location: PostLocationDto?,
    val likes: List<String>,
    val recipeId: PostRecipeDto?,
    val likesCount: Int,
    val commentsCount: Int,
    val isLiked: Boolean,
    val isOwner: Boolean,
    val isFollowingAuthor: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

data class PostLocationDto(
    val type: String?,
    val coordinates: List<Double>?,
    val placeName: String?
)

// ── Comment ─────────────────────────────────────────────────────────────────

data class CommentAuthorDto(
    @SerializedName("_id") val id: String,
    val displayName: String,
    val profileImage: String?
)

data class CommentDto(
    @SerializedName("_id") val id: String,
    val postId: String,
    val authorUserId: CommentAuthorDto,
    val text: String,
    val isOwner: Boolean,
    val createdAt: String,
    val updatedAt: String
)

// ── Linked Recipe (populated from recipeId) ────────────────────────────────

data class PostRecipeDto(
    @SerializedName("_id") val id: String,
    val title: String?,
    val description: String?,
    val cookingTime: String?,
    val difficulty: String?,
    val imageUrl: String?
)

// ── Requests ────────────────────────────────────────────────────────────────

data class CreatePostRequest(
    val title: String,
    val text: String,
    val mediaUrls: List<String> = emptyList(),
    val recipeId: String? = null,
    val location: PostLocationRequest? = null
)

data class UpdatePostRequest(
    val title: String? = null,
    val text: String? = null,
    val mediaUrls: List<String>? = null,
    val location: PostLocationRequest? = null
)

data class PostLocationRequest(
    val lat: Double,
    val lng: Double,
    val placeName: String? = null
)

data class CreateCommentRequest(
    val text: String
)

data class UpdateCommentRequest(
    val text: String
)

// ── Responses ───────────────────────────────────────────────────────────────

data class PostListResponse(
    val items: List<PostDto>,
    val total: Int,
    val page: Int,
    val limit: Int
)

data class CommentsListResponse(
    val items: List<CommentDto>,
    val total: Int
)

data class ToggleLikeResponse(
    val liked: Boolean,
    val likesCount: Int
)

data class UploadImageResponse(
    val imageUrl: String
)
