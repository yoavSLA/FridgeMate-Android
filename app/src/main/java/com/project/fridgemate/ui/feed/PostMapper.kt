package com.project.fridgemate.ui.feed

import com.project.fridgemate.data.remote.dto.CommentDto
import com.project.fridgemate.data.remote.dto.PostDto

/** Shared mappers from server DTOs to UI models. */

fun PostDto.toPost(): Post {
    val loc = location
    val authorAddr = authorUserId.address

    // Priority: Post's specific location, then Author's registered location
    val lat = loc?.coordinates?.getOrNull(1) ?: authorAddr?.lat ?: 0.0
    val lng = loc?.coordinates?.getOrNull(0) ?: authorAddr?.lng ?: 0.0

    val placeName = loc?.placeName
    val city = authorAddr?.city

    val recipe = recipeId?.let {
        LinkedRecipe(
            id = it.id,
            title = it.title ?: "",
            cookingTime = it.cookingTime ?: "",
            difficulty = it.difficulty ?: "",
            imageUrl = it.imageUrl ?: ""
        )
    }

    return Post(
        id = id,
        authorId = authorUserId.id,
        userName = authorUserId.displayName,
        userLocation = placeName ?: city ?: "",
        postTitle = title ?: "",
        description = text,
        likesCount = likesCount,
        commentsCount = commentsCount,
        imageUrl = mediaUrls.firstOrNull() ?: "",
        authorImageUrl = authorUserId.profileImage ?: "",
        isLiked = isLiked,
        isOwner = isOwner,
        latitude = lat,
        longitude = lng,
        linkedRecipe = recipe,
        createdAt = createdAt,
        isFollowingAuthor = isFollowingAuthor
    )
}

fun CommentDto.toComment(): Comment {
    return Comment(
        id = id,
        postId = postId,
        userName = authorUserId.displayName,
        text = text,
        authorImageUrl = authorUserId.profileImage ?: "",
        isOwner = isOwner,
        createdAt = createdAt
    )
}
