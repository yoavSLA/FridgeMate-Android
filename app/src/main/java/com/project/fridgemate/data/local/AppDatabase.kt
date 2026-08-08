package com.project.fridgemate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.project.fridgemate.data.local.dao.FridgeDao
import com.project.fridgemate.data.local.dao.InventoryItemDao
import com.project.fridgemate.data.local.dao.NotificationDao
import com.project.fridgemate.data.local.dao.PostDao
import com.project.fridgemate.data.local.dao.RecipeDao
import com.project.fridgemate.data.local.dao.ScanDao
import com.project.fridgemate.data.local.dao.UserDao
import com.project.fridgemate.data.local.dao.JournalDao
import com.project.fridgemate.data.local.entity.FridgeEntity
import com.project.fridgemate.data.local.entity.InventoryItemEntity
import com.project.fridgemate.data.local.entity.JournalEntity
import com.project.fridgemate.data.local.entity.NotificationEntity
import com.project.fridgemate.data.local.entity.PostEntity
import com.project.fridgemate.data.local.entity.RecipeEntity
import com.project.fridgemate.data.local.entity.ScanEntity
import com.project.fridgemate.data.local.entity.UserEntity

@Database(
    entities = [RecipeEntity::class, PostEntity::class, FridgeEntity::class, UserEntity::class, InventoryItemEntity::class, JournalEntity::class, ScanEntity::class, NotificationEntity::class],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun postDao(): PostDao
    abstract fun fridgeDao(): FridgeDao
    abstract fun userDao(): UserDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun journalDao(): JournalDao
    abstract fun scanDao(): ScanDao
    abstract fun notificationDao(): NotificationDao

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
                    // Everything stored here is a re-fetchable cache, so a version bump
                    // rebuilds the database rather than carrying hand-written migrations.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
