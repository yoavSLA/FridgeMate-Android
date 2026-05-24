package com.project.fridgemate.ui.journal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.model.JournalEntry
import com.project.fridgemate.data.remote.dto.CreateJournalRequest
import com.project.fridgemate.data.remote.dto.JournalEntryDto
import com.project.fridgemate.data.remote.dto.JournalMealDto
import com.project.fridgemate.data.remote.dto.UpdateJournalRequest
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.JournalRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.getJournals()) {
                is FridgeResult.Success -> {
                    _entries.value = result.data.items.map { it.toJournalEntry() }
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                }
                else -> {}
            }
            _isLoading.value = false
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
                        mealType = if (entry.mealType.isNotEmpty()) entry.mealType else "SNACK",
                        recipeId = null,
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
                    val currentList = _entries.value?.toMutableList() ?: mutableListOf()
                    currentList.add(0, result.data.toJournalEntry())
                    _entries.value = currentList
                    _actionSuccess.value = true
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
                        mealType = if (updatedEntry.mealType.isNotEmpty()) updatedEntry.mealType else "SNACK",
                        recipeId = null,
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
                    val currentList = _entries.value?.toMutableList() ?: return@launch
                    val index = currentList.indexOfFirst { it.id == updatedEntry.id }
                    if (index != -1) {
                        currentList[index] = result.data.toJournalEntry()
                        _entries.value = currentList
                    }
                    _actionSuccess.value = true
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

    private fun JournalEntryDto.toJournalEntry(): JournalEntry {
        val meal = meals.firstOrNull()
        val time = try {
            dateFormat.parse(date)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        return JournalEntry(
            id = id,
            title = title,
            content = content ?: "",
            mealType = meal?.mealType ?: "",
            mood = mood ?: "",
            calories = meal?.calories?.toString() ?: "",
            macros = meal?.notes ?: "",
            imageUrl = imageUrl,
            dateMillis = time
        )
    }
}
