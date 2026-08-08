package com.project.fridgemate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.fridgemate.data.local.entity.InventoryItemEntity

@Dao
interface InventoryItemDao {

    @Query("SELECT * FROM inventory_items")
    suspend fun getAll(): List<InventoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventoryItemEntity>)

    @Query("DELETE FROM inventory_items")
    suspend fun deleteAll()

    @Query("UPDATE inventory_items SET ownerId = :ownerId WHERE id = :itemId")
    suspend fun updateOwnerId(itemId: String, ownerId: String?)
}
