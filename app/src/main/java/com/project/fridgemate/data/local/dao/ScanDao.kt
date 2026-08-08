package com.project.fridgemate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.fridgemate.data.local.entity.ScanEntity

@Dao
interface ScanDao {

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getById(id: String): ScanEntity?

    @Query("SELECT * FROM scans ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(): ScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: ScanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scans: List<ScanEntity>)

    @Query("DELETE FROM scans")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM scans WHERE id NOT IN " +
            "(SELECT id FROM scans ORDER BY createdAt DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}
