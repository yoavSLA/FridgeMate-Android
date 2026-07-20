package com.project.fridgemate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val userName: String?,
    val profileImage: String?,
    val bio: String? = null,
    val role: String,
    val allergiesJson: String = "[]",
    val dietPreference: String,
    val activeFridgeId: String?,
    val addressJson: String? = null
)
