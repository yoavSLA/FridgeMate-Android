package com.project.fridgemate.data.model

data class JournalEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val mealType: String = "",
    val mood: String = "",
    val calories: String = "",
    val macros: String = "",
    val imageUrl: String? = null
)
