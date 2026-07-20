package com.project.fridgemate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val fridgeId: String,
    val ownerId: String?,
    val name: String,
    val quantity: String,
    val ownership: String,
    val isRunningLow: Boolean
)
