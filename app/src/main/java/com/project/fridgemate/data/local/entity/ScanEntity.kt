package com.project.fridgemate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey val id: String,
    val fridgeId: String,
    /** ISO-8601 UTC, so it also sorts correctly as text. */
    val createdAt: String,
    val scanJson: String,
    val cachedAt: Long = System.currentTimeMillis()
)
