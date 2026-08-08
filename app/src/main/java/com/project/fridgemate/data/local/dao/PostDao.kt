package com.project.fridgemate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.fridgemate.data.local.entity.PostEntity

@Dao
interface PostDao {

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    suspend fun getAll(): List<PostEntity>

    @Query("SELECT * FROM posts WHERE isOwner = 1 ORDER BY createdAt DESC")
    suspend fun getMyPosts(): List<PostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: PostEntity)

    @Query("DELETE FROM posts")
    suspend fun clearAll()

    @Query("DELETE FROM posts WHERE isOwner = 0")
    suspend fun clearNonOwnerPosts()

    @Query(
        "DELETE FROM posts WHERE isOwner = 0 AND id NOT IN " +
            "(SELECT id FROM posts WHERE isOwner = 0 ORDER BY createdAt DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deleteById(postId: String)
}
