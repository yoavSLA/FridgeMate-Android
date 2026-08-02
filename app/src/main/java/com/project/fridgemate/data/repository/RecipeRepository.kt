package com.project.fridgemate.data.repository

import androidx.lifecycle.LiveData
import com.google.gson.Gson
import com.project.fridgemate.data.local.dao.RecipeDao
import com.project.fridgemate.data.local.entity.RecipeEntity
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.RecipeApi
import com.project.fridgemate.data.remote.dto.GenerateRecipesRequest
import com.project.fridgemate.data.remote.dto.RecipeIngredientDto
import com.project.fridgemate.data.remote.dto.ServerRecipeDto

class RecipeRepository(private val recipeDao: RecipeDao) : BaseRepository() {

    private val recipeApi: RecipeApi = ApiClient.createApi(RecipeApi::class.java)
    private val gson = Gson()

    companion object {
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
    }

    fun getRecommended(): LiveData<List<RecipeEntity>> =
        recipeDao.getByType(RecipeEntity.TYPE_RECOMMENDED)

    fun getFavorites(): LiveData<List<RecipeEntity>> =
        recipeDao.getByType(RecipeEntity.TYPE_FAVORITE)

    fun getByRoomId(roomId: Long): LiveData<RecipeEntity?> =
        recipeDao.getById(roomId)

    fun getByServerId(serverId: String): LiveData<RecipeEntity?> =
        recipeDao.getByServerId(serverId)

    suspend fun hasRecommended(): Boolean {
        return recipeDao.getByTypeSync(RecipeEntity.TYPE_RECOMMENDED).isNotEmpty()
    }

    suspend fun isCacheExpired(): Boolean {
        val lastCache = recipeDao.getLatestCacheTime(RecipeEntity.TYPE_RECOMMENDED) ?: return true
        return System.currentTimeMillis() - lastCache > CACHE_TTL_MS
    }

    suspend fun clearRecommendedCache() {
        recipeDao.deleteByType(RecipeEntity.TYPE_RECOMMENDED)
    }

    suspend fun fetchRecommended(
        ingredients: List<String>,
        count: Int = 3
    ): Result<Unit> {
        return try {
            val response = recipeApi.generateRecipes(
                GenerateRecipesRequest(ingredients, count)
            )
            if (response.isSuccessful) {
                val recipes = response.body()?.recipes ?: emptyList()
                val entities = recipes.map { it.toEntity(RecipeEntity.TYPE_RECOMMENDED) }
                recipeDao.deleteByType(RecipeEntity.TYPE_RECOMMENDED)
                recipeDao.insertAll(entities)
                Result.success(Unit)
            } else {
                val error = parseError(response.errorBody()?.string())
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFavorites(): Result<Unit> {
        return try {
            val response = recipeApi.getUserFavorites()
            if (response.isSuccessful) {
                val recipes = response.body()?.items ?: emptyList()
                val entities = recipes.map {
                    it.toEntity(RecipeEntity.TYPE_FAVORITE).copy(isFavorite = true)
                }
                recipeDao.deleteByType(RecipeEntity.TYPE_FAVORITE)
                recipeDao.insertAll(entities)
                syncFavoriteFlags()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to load favorites"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * After fetching favorites, mark any recommended recipes that the user
     * has also favorited so the star icon shows correctly on both tabs.
     */
    private suspend fun syncFavoriteFlags() {
        val favorites = recipeDao.getByTypeSync(RecipeEntity.TYPE_FAVORITE)
        val favServerIds = favorites.mapNotNull { it.serverId }.toSet()
        val recommended = recipeDao.getByTypeSync(RecipeEntity.TYPE_RECOMMENDED)
        for (r in recommended) {
            val shouldBeFav = r.serverId != null && r.serverId in favServerIds
            if (r.isFavorite != shouldBeFav) {
                recipeDao.updateFavorite(r.id, shouldBeFav)
            }
        }
    }

    suspend fun favoriteRecipe(serverId: String): Result<Unit> {
        return try {
            val response = recipeApi.favoriteRecipe(serverId)
            if (response.isSuccessful || response.code() == 409) {
                recipeDao.updateFavoriteByServerId(serverId, true)
                val existing = recipeDao.getByServerIdSync(serverId)
                if (existing != null && existing.type == RecipeEntity.TYPE_RECOMMENDED) {
                    recipeDao.insert(existing.copy(
                        id = 0,
                        type = RecipeEntity.TYPE_FAVORITE,
                        isFavorite = true,
                        cachedAt = System.currentTimeMillis()
                    ))
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to favorite recipe"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unfavoriteRecipe(serverId: String): Result<Unit> {
        return try {
            val response = recipeApi.unfavoriteRecipe(serverId)
            if (response.isSuccessful) {
                recipeDao.updateFavoriteByServerId(serverId, false)
                recipeDao.deleteByServerIdAndType(serverId, RecipeEntity.TYPE_FAVORITE)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to unfavorite recipe"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAndCacheRecipeByServerId(serverId: String): Result<RecipeEntity> {
        val existing = recipeDao.getByServerIdSync(serverId)
        if (existing != null) return Result.success(existing)

        return try {
            val response = recipeApi.getRecipeById(serverId)
            if (response.isSuccessful) {
                val dto = response.body()!!
                val entity = dto.toEntity(RecipeEntity.TYPE_FEED)
                val roomId = recipeDao.insert(entity)
                Result.success(entity.copy(id = roomId))
            } else {
                Result.failure(Exception("Recipe not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ServerRecipeDto.toEntity(type: String): RecipeEntity {
        return RecipeEntity(
            serverId = id,
            title = title,
            description = description ?: "",
            cookingTime = cookingTime ?: "",
            difficulty = difficulty ?: "Medium",
            ingredientsJson = gson.toJson(ingredients ?: emptyList<RecipeIngredientDto>()),
            stepsJson = gson.toJson(steps ?: emptyList<String>()),
            calories = nutrition?.calories ?: "",
            protein = nutrition?.protein ?: "",
            carbs = nutrition?.carbs ?: "",
            fat = nutrition?.fat ?: "",
            imageUrl = imageUrl ?: "",
            type = type,
            isFavorite = isFavorited ?: false
        )
    }
}
