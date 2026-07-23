package com.project.fridgemate.ui.recipes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.R
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.RecipeEntity
import com.project.fridgemate.data.repository.FridgeRepository
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.InventoryItemRepository
import com.project.fridgemate.data.repository.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class RecipesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecipeRepository
    private val fridgeRepository = FridgeRepository(application.applicationContext)
    private val inventoryRepository = InventoryItemRepository(application.applicationContext)

    val recommended: LiveData<List<RecipeEntity>>
    val favorites: LiveData<List<RecipeEntity>>

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading


    private var recommendedJob: Job? = null

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _noFridge = MutableLiveData(false)
    val noFridge: LiveData<Boolean> = _noFridge

    private val _fridgeEmpty = MutableLiveData(false)
    val fridgeEmpty: LiveData<Boolean> = _fridgeEmpty

    init {
        val dao = AppDatabase.getInstance(application).recipeDao()
        repository = RecipeRepository(dao)
        recommended = repository.getRecommended()
        favorites = repository.getFavorites()

        viewModelScope.launch { repository.fetchFavorites() }
    }

    fun loadRecommendedIfNeeded() {
        if (recommendedJob?.isActive == true) return
        viewModelScope.launch {
            if (repository.isCacheExpired()) {
                loadRecommended()
            } else {
                _noFridge.value = false
            }
        }
    }

    fun loadRecommended() {
        if (recommendedJob?.isActive == true) return
        _error.value = null
        recommendedJob = viewModelScope.launch {
            val cached = inventoryRepository.getCachedItems()
            if (cached.isNotEmpty()) {
                _isLoading.value = true
            }

            val ingredients = fetchFridgeIngredients()
            if (ingredients == null) {
                _isLoading.value = false
                return@launch
            }
            if (ingredients.isEmpty()) {
                _isLoading.value = false
                _fridgeEmpty.value = true
                return@launch
            }
            _fridgeEmpty.value = false
            _isLoading.value = true
            val result = repository.fetchRecommended(ingredients)
            if (result.isFailure) {
                _error.value = friendlyError(result.exceptionOrNull())
            }
            _isLoading.value = false
        }
    }

    private suspend fun fetchFridgeIngredients(): List<String>? {
        return when (val fridgeResult = fridgeRepository.getMyFridge()) {
            is FridgeResult.Success -> {
                _noFridge.postValue(false)
                val items = inventoryRepository.getItems(fridgeResult.data.id, mineOrUnowned = true)
                items.map { "${it.name} (${it.quantity})" }
            }
            is FridgeResult.NoFridge -> {
                _noFridge.postValue(true)
                repository.clearRecommendedCache()
                null
            }
            is FridgeResult.Error -> {
                _error.value = friendlyError(Exception(fridgeResult.message))
                null
            }
        }
    }

    private val _detailLoading = MutableLiveData(false)
    val detailLoading: LiveData<Boolean> = _detailLoading

    fun getRecipeByRoomId(roomId: Long): LiveData<RecipeEntity?> = repository.getByRoomId(roomId)

    fun getRecipeByServerId(serverId: String): LiveData<RecipeEntity?> = repository.getByServerId(serverId)

    fun fetchRecipeDetail(serverId: String) {
        _error.value = null
        _detailLoading.value = true
        viewModelScope.launch {
            val result = repository.fetchAndCacheRecipeByServerId(serverId)
            if (result.isFailure) {
                _error.value = friendlyError(result.exceptionOrNull())
            }
            _detailLoading.value = false
        }
    }

    private fun friendlyError(e: Throwable?): String {
        val ctx = getApplication<Application>()
        return when {
            e is UnknownHostException || e is ConnectException ->
                ctx.getString(R.string.error_no_connection)
            e is SocketTimeoutException ->
                ctx.getString(R.string.error_timeout)
            e?.message?.contains("500") == true || e?.message?.contains("502") == true ||
                e?.message?.contains("503") == true ->
                ctx.getString(R.string.error_server)
            e?.message?.contains("401") == true || e?.message?.contains("403") == true ->
                ctx.getString(R.string.error_auth_expired)
            e?.message?.contains("429") == true ->
                ctx.getString(R.string.error_rate_limit)
            else ->
                ctx.getString(R.string.error_generic)
        }
    }

    fun toggleFavorite(recipe: RecipeEntity) {
        val serverId = recipe.serverId ?: return
        val wasFavorite = recipe.isFavorite
        
        viewModelScope.launch {
            val result = if (wasFavorite) {
                repository.unfavoriteRecipe(serverId)
            } else {
                repository.favoriteRecipe(serverId)
            }
            
            if (result.isFailure) {
                _error.postValue(friendlyError(result.exceptionOrNull()))
            }
        }
    }
}
