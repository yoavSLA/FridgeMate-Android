package com.project.fridgemate.ui.journal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project.fridgemate.data.model.JournalEntry

class JournalViewModel : ViewModel() {

    private val _entries = MutableLiveData<List<JournalEntry>>(emptyList())
    val entries: LiveData<List<JournalEntry>> = _entries

    init {
        // Load mock data
        val mockData = listOf(
            JournalEntry(
                title = "Healthy Breakfast",
                content = "Oatmeal with berries and a cup of green tea. Felt very energized!",
                mealType = "Breakfast",
                mood = "😊",
                calories = "350",
                macros = "15g P / 45g C / 8g F",
                dateMillis = System.currentTimeMillis() - 86400000 // 1 day ago
            ),
            JournalEntry(
                title = "Post-workout Lunch",
                content = "Chicken salad with quinoa and avocado. Needed the protein.",
                mealType = "Lunch",
                mood = "💪",
                calories = "520",
                macros = "40g P / 35g C / 22g F",
                dateMillis = System.currentTimeMillis() - 40000000 // half day ago
            )
        )
        _entries.value = mockData
    }

    fun addEntry(entry: JournalEntry) {
        val currentList = _entries.value?.toMutableList() ?: mutableListOf()
        currentList.add(0, entry) // Add to top
        _entries.value = currentList
    }

    fun updateEntry(updatedEntry: JournalEntry) {
        val currentList = _entries.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            currentList[index] = updatedEntry
            _entries.value = currentList
        }
    }

    fun deleteEntry(id: String) {
        val currentList = _entries.value?.toMutableList() ?: return
        currentList.removeAll { it.id == id }
        _entries.value = currentList
    }

    fun getEntryById(id: String): JournalEntry? {
        return _entries.value?.find { it.id == id }
    }
}
