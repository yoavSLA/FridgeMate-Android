package com.project.fridgemate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.fridgemate.data.local.entity.NotificationEntity

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAll(): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    /** Keeps the cache bounded; the screen only ever renders the newest entries anyway. */
    @Query(
        "DELETE FROM notifications WHERE id NOT IN " +
            "(SELECT id FROM notifications ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}
