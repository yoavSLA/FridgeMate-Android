package com.project.fridgemate.ui.fridge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.dto.InventoryItemDto
import com.project.fridgemate.data.repository.ChatResult
import com.project.fridgemate.data.repository.FridgeChatRepository
import com.project.fridgemate.data.repository.FridgeRepository
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.InventoryItemRepository
import kotlinx.coroutines.launch

class FridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val fridgeRepository = FridgeRepository(application.applicationContext)
    private val itemRepository = InventoryItemRepository(application.applicationContext)
    private val chatRepository = FridgeChatRepository()

    sealed class State {
        object Loading : State()
        data class Items(val items: List<FridgeItem>) : State()
        object Empty : State()
        object NoFridge : State()
        object NotLoggedIn : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableLiveData<State>(State.Loading)
    val state: LiveData<State> = _state

    private val _activeFridgeId = MutableLiveData<String?>(null)
    val activeFridgeId: LiveData<String?> = _activeFridgeId

    private val _activeFridgeName = MutableLiveData<String?>(null)
    val activeFridgeName: LiveData<String?> = _activeFridgeName

    private val _unreadCount = MutableLiveData<Int>(0)
    val unreadCount: LiveData<Int> = _unreadCount

    fun refreshUnreadCount() {
        val fridgeId = _activeFridgeId.value ?: return
        viewModelScope.launch {
            when (val result = chatRepository.getUnreadCount(fridgeId)) {
                is ChatResult.Success -> _unreadCount.value = result.data
                is ChatResult.Error -> { /* keep previous value */ }
            }
        }
    }

    fun loadItems() {
        if (!ApiClient.getTokenManager().isLoggedIn) {
            _state.value = State.NotLoggedIn
            return
        }
        viewModelScope.launch {
            val cached = itemRepository.getCachedItems()
            if (cached.isNotEmpty()) {
                _state.value = State.Items(buildFridgeItemList(cached))
            } else {
                // No cache yet — show spinner on first load
                _state.value = State.Loading
            }

            // Refresh from network in background
            when (val fridgeResult = fridgeRepository.getMyFridge()) {
                is FridgeResult.NoFridge -> {
                    itemRepository.clearCache()
                    _activeFridgeId.value = null
                    _activeFridgeName.value = null
                    _unreadCount.value = 0
                    _state.value = State.NoFridge
                }
                is FridgeResult.Error -> {
                    if (cached.isEmpty()) _state.value = State.Error(fridgeResult.message)
                }
                is FridgeResult.Success -> {
                    _activeFridgeId.value = fridgeResult.data.id
                    _activeFridgeName.value = fridgeResult.data.name
                    val items = itemRepository.getItems(fridgeResult.data.id)
                    _state.value = if (items.isEmpty()) State.Empty
                                   else State.Items(buildFridgeItemList(items))
                    refreshUnreadCount()
                }
            }
        }
    }

    private fun buildFridgeItemList(items: List<InventoryItemDto>): List<FridgeItem> {
        val result = mutableListOf<FridgeItem>()
        val lowItems = items.filter { it.isRunningLow }
        if (lowItems.isNotEmpty()) {
            result.add(FridgeItem.RunningLow(lowItems.map { it.name }))
        }
        result.add(FridgeItem.CategoryHeader("Items"))
        items.forEach { result.add(FridgeItem.Product(it.name, it.quantity, it.isRunningLow)) }
        return result
    }
}
