package com.project.fridgemate.ui.journal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.R
import com.project.fridgemate.data.model.JournalEntry
import com.project.fridgemate.data.remote.dto.CreateJournalRequest
import com.project.fridgemate.data.remote.dto.JournalEntryDto
import com.project.fridgemate.data.remote.dto.JournalMealDto
import com.project.fridgemate.data.remote.dto.UpdateJournalRequest
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.JournalRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = JournalRepository(application.applicationContext)

    private val _entries = MutableLiveData<List<JournalEntry>>(emptyList())
    val entries: LiveData<List<JournalEntry>> = _entries

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var isFirstLoad = true

    private val _actionSuccess = MutableLiveData<Boolean?>(null)
    val actionSuccess: LiveData<Boolean?> = _actionSuccess

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        loadEntries()
    }

    fun resetActionState() {
        _actionSuccess.value = null
        _error.value = null
    }

    fun loadEntries() {
        if (_isLoading.value == true) return

        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                _isLoading.value = true
                _error.value = null
                
                if (isFirstLoad) {
                    val cached = repository.getCachedJournals()
                    if (cached.isNotEmpty()) {
                        _entries.value = cached.mapNotNull { it.toJournalEntry() }
                    }
                }

                val result = repository.getJournals()
                
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 1000) kotlinx.coroutines.delay(1000 - elapsed)

                when (result) {
                    is FridgeResult.Success -> {
                        _entries.value = result.data.items?.mapNotNull { it.toJournalEntry() } ?: emptyList()
                        isFirstLoad = false
                    }
                    is FridgeResult.Error -> {
                        val cached = repository.getCachedJournals()
                        if (cached.isNotEmpty()) {
                            _entries.value = cached.mapNotNull { it.toJournalEntry() }
                        }
                        _error.value = result.message
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: getApplication<Application>().getString(R.string.error_generic)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addEntry(entry: JournalEntry) {
        viewModelScope.launch {
            _isLoading.value = true
            val request = CreateJournalRequest(
                title = entry.title,
                content = entry.content,
                date = dateFormat.format(Date(entry.dateMillis)),
                meals = listOf(
                    JournalMealDto(
                        mealType = if (entry.mealType.isNotEmpty()) entry.mealType.uppercase(Locale.US) else "SNACK",
                        recipeId = entry.recipeId?.let { com.google.gson.JsonPrimitive(it) },
                        customRecipeTitle = null,
                        calories = entry.calories.toIntOrNull(),
                        notes = entry.macros
                    )
                ),
                rating = null,
                mood = entry.mood,
                imageUrl = entry.imageUrl
            )

            when (val result = repository.createJournal(request)) {
                is FridgeResult.Success -> {
                    val journalEntry = result.data.toJournalEntry()
                    if (journalEntry != null) {
                        val currentList = _entries.value?.toMutableList() ?: mutableListOf()
                        currentList.add(0, journalEntry)
                        _entries.value = currentList
                        _actionSuccess.value = true
                    } else {
                        _error.value = getApplication<Application>().getString(R.string.error_journal_parse_failed)
                        _actionSuccess.value = false
                    }
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    _actionSuccess.value = false
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun updateEntry(updatedEntry: JournalEntry) {
        viewModelScope.launch {
            _isLoading.value = true
            val request = UpdateJournalRequest(
                title = updatedEntry.title,
                content = updatedEntry.content,
                date = dateFormat.format(Date(updatedEntry.dateMillis)),
                meals = listOf(
                    JournalMealDto(
                        mealType = if (updatedEntry.mealType.isNotEmpty()) updatedEntry.mealType.uppercase(Locale.US) else "SNACK",
                        recipeId = updatedEntry.recipeId?.let { com.google.gson.JsonPrimitive(it) },
                        customRecipeTitle = null,
                        calories = updatedEntry.calories.toIntOrNull(),
                        notes = updatedEntry.macros
                    )
                ),
                rating = null,
                mood = updatedEntry.mood,
                imageUrl = updatedEntry.imageUrl
            )

            when (val result = repository.updateJournal(updatedEntry.id, request)) {
                is FridgeResult.Success -> {
                    val journalEntry = result.data.toJournalEntry()
                    if (journalEntry != null) {
                        val currentList = _entries.value?.toMutableList() ?: return@launch
                        val index = currentList.indexOfFirst { it.id == updatedEntry.id }
                        if (index != -1) {
                            currentList[index] = journalEntry
                            _entries.value = currentList
                        }
                        _actionSuccess.value = true
                    } else {
                        _error.value = getApplication<Application>().getString(R.string.error_journal_parse_failed)
                        _actionSuccess.value = false
                    }
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    _actionSuccess.value = false
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.deleteJournal(id)) {
                is FridgeResult.Success -> {
                    val currentList = _entries.value?.toMutableList() ?: return@launch
                    currentList.removeAll { it.id == id }
                    _entries.value = currentList
                    _actionSuccess.value = true
                    loadEntries()
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    _actionSuccess.value = false
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    suspend fun uploadImage(imageBytes: ByteArray, mimeType: String): String? {
        return when (val result = repository.uploadImage(imageBytes, mimeType)) {
            is FridgeResult.Success -> result.data
            is FridgeResult.Error -> {
                _error.value = result.message
                null
            }
            else -> null
        }
    }

    fun getEntryById(id: String): JournalEntry? {
        return _entries.value?.find { it.id == id }
    }

    fun addRecipeToJournal(recipe: com.project.fridgemate.data.local.entity.RecipeEntity, timestamp: Long) {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val mealType = when (calendar[Calendar.HOUR_OF_DAY]) {
            in 5..10 -> "Breakfast"
            in 11..15 -> "Lunch"
            in 16..21 -> "Dinner"
            else -> "Snack"
        }

        val nutritionInfo = buildString {
            if (recipe.protein.isNotBlank()) append("${recipe.protein} P / ")
            if (recipe.carbs.isNotBlank()) append("${recipe.carbs} C / ")
            if (recipe.fat.isNotBlank()) append("${recipe.fat} F")
        }.trimEnd(' ', '/', ' ')

        val entry = JournalEntry(
            title = recipe.title,
            content = recipe.description.ifBlank { "Recipe added from collections." },
            dateMillis = timestamp,
            mealType = mealType,
            calories = recipe.calories.replace(Regex("\\D"), ""),
            macros = nutritionInfo,
            imageUrl = recipe.imageUrl,
            recipeId = recipe.serverId
        )
        addEntry(entry)
    }

    private fun JournalEntryDto.toJournalEntry(): JournalEntry? {
        return try {
            val meal = meals?.firstOrNull()
            val time = if (date.isNullOrEmpty()) {
                System.currentTimeMillis()
            } else {
                try {
                    dateFormat.parse(date)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
            }

            JournalEntry(
                id = id ?: java.util.UUID.randomUUID().toString(),
                title = title ?: "Untitled Entry",
                content = content ?: "",
                mealType = meal?.mealType ?: "",
                mood = mood ?: "",
                calories = meal?.calories?.toString() ?: "",
                macros = meal?.notes ?: "",
                imageUrl = imageUrl,
                dateMillis = time,
                recipeId = meal?.recipeId?.let { 
                    if (it.isJsonPrimitive) it.asString 
                    else if (it.isJsonObject) it.asJsonObject.get("id")?.asString ?: it.asJsonObject.get("_id")?.asString
                    else it.toString() 
                }
            )
        } catch (_: Exception) {
            null
        }
    }
}
