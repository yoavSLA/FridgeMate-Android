package com.project.fridgemate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.project.fridgemate.data.local.dao.FridgeDao
import com.project.fridgemate.data.local.dao.InventoryItemDao
import com.project.fridgemate.data.local.dao.PostDao
import com.project.fridgemate.data.local.dao.RecipeDao
import com.project.fridgemate.data.local.dao.UserDao
import com.project.fridgemate.data.local.dao.JournalDao
import com.project.fridgemate.data.local.entity.FridgeEntity
import com.project.fridgemate.data.local.entity.InventoryItemEntity
import com.project.fridgemate.data.local.entity.JournalEntity
import com.project.fridgemate.data.local.entity.PostEntity
import com.project.fridgemate.data.local.entity.RecipeEntity
import com.project.fridgemate.data.local.entity.UserEntity

@Database(
    entities = [RecipeEntity::class, PostEntity::class, FridgeEntity::class, UserEntity::class, InventoryItemEntity::class, JournalEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun postDao(): PostDao
    abstract fun fridgeDao(): FridgeDao
    abstract fun userDao(): UserDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun journalDao(): JournalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fridgemate_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
