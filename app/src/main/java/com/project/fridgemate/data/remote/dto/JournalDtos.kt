package com.project.fridgemate.data.remote.dto

data class JournalEntryDto(
    val id: String,
    val userId: String,
    val title: String,
    val content: String?,
    val date: String, // ISO String
    val meals: List<JournalMealDto>,
    val rating: Int?,
    val mood: String?,
    val imageUrl: String?,
    val createdAt: String,
    val updatedAt: String
)

data class JournalMealDto(
    val mealType: String,
    val recipeId: String?,
    val customRecipeTitle: String?,
    val calories: Int?,
    val notes: String?
)

data class CreateJournalRequest(
    val title: String,
    val content: String?,
    val date: String,
    val meals: List<JournalMealDto>,
    val rating: Int?,
    val mood: String?,
    val imageUrl: String?
)

data class UpdateJournalRequest(
    val title: String?,
    val content: String?,
    val date: String?,
    val meals: List<JournalMealDto>?,
    val rating: Int?,
    val mood: String?,
    val imageUrl: String?
)

data class JournalListResponse(
    val items: List<JournalEntryDto>,
    val total: Int,
    val page: Int,
    val totalPages: Int
)

data class JournalResponse(
    val success: Boolean,
    val data: JournalEntryDto
)
