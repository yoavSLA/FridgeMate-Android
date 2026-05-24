package com.project.fridgemate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val date: Long,
    val mealType: String,
    val calories: String,
    val macros: String,
    val mood: String,
    val imageUrl: String,
    val cachedAt: Long = System.currentTimeMillis()
)
