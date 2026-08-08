package com.project.fridgemate.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipes", indices = [Index(value = ["serverId", "type"])])
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String? = null,
    val title: String,
    val description: String = "",
    val cookingTime: String = "",
    val difficulty: String = "Medium",
    val ingredientsJson: String = "[]",
    val stepsJson: String = "[]",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val imageUrl: String = "",
    val type: String = TYPE_RECOMMENDED,
    val isFavorite: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_RECOMMENDED = "RECOMMENDED"
        const val TYPE_FAVORITE = "FAVORITE"
        const val TYPE_FEED = "FEED"
    }
}
