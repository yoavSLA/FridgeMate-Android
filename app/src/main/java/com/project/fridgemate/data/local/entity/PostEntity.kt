package com.project.fridgemate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val authorId: String = "",
    val authorName: String,
    val authorLocation: String,
    val authorProfileImage: String = "",
    val title: String,
    val text: String,
    val imageUrl: String = "",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLiked: Boolean = false,
    val isOwner: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: String = "",
    val cachedAt: Long = System.currentTimeMillis(),
    val recipeId: String? = null,
    val recipeTitle: String? = null,
    val recipeCookingTime: String? = null,
    val recipeDifficulty: String? = null,
    val recipeImageUrl: String? = null
)
