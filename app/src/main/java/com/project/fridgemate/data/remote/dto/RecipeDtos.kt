package com.project.fridgemate.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GenerateRecipesRequest(
    val ingredients: List<String>,
    val count: Int = 3
)

data class GenerateRecipesResponse(
    val message: String,
    val recipes: List<ServerRecipeDto>,
    val count: Int
)

data class ServerRecipeDto(
    @SerializedName("_id") val id: String,
    val title: String,
    val description: String?,
    val cookingTime: String?,
    val difficulty: String?,
    val ingredients: List<RecipeIngredientDto>?,
    val steps: List<String>?,
    val nutrition: RecipeNutritionDto?,
    val imageUrl: String?,
    val isFavorited: Boolean? = null
)

data class RecipeIngredientDto(
    val name: String,
    val amount: String
)

data class RecipeNutritionDto(
    val calories: String?,
    val protein: String?,
    val carbs: String?,
    val fat: String?
)
