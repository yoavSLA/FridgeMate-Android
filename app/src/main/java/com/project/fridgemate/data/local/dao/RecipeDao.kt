package com.project.fridgemate.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.fridgemate.data.local.entity.RecipeEntity

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes WHERE type = :type ORDER BY cachedAt DESC")
    fun getByType(type: String): LiveData<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE type = :type ORDER BY cachedAt DESC")
    suspend fun getByTypeSync(type: String): List<RecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<RecipeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: RecipeEntity): Long

    @Query("DELETE FROM recipes WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM recipes WHERE serverId = :serverId AND type = :type")
    suspend fun deleteByServerIdAndType(serverId: String, type: String)

    @Query("UPDATE recipes SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE recipes SET isFavorite = :isFavorite WHERE serverId = :serverId")
    suspend fun updateFavoriteByServerId(serverId: String, isFavorite: Boolean)

    @Query("SELECT MAX(cachedAt) FROM recipes WHERE type = :type")
    suspend fun getLatestCacheTime(type: String): Long?

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    fun getById(id: Long): LiveData<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE serverId = :serverId ORDER BY cachedAt DESC LIMIT 1")
    fun getByServerId(serverId: String): LiveData<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE serverId = :serverId ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getByServerIdSync(serverId: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE serverId = :serverId AND type = :type LIMIT 1")
    suspend fun getByServerIdAndTypeSync(serverId: String, type: String): RecipeEntity?

    @Query("DELETE FROM recipes WHERE type = :type AND id NOT IN (SELECT id FROM recipes WHERE type = :type ORDER BY cachedAt DESC LIMIT :keep)")
    suspend fun trimType(type: String, keep: Int)
}
