package com.project.fridgemate.ui.fridge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.R
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.dto.FridgeMemberDetailDto
import com.project.fridgemate.data.remote.dto.InventoryItemDto
import com.project.fridgemate.data.remote.socket.SocketManager
import com.project.fridgemate.data.repository.ChatResult
import com.project.fridgemate.data.repository.FridgeChatRepository
import com.project.fridgemate.data.repository.FridgeRepository
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.InventoryItemRepository
import io.socket.emitter.Emitter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

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

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _activeFridgeId = MutableLiveData<String?>(null)
    val activeFridgeId: LiveData<String?> = _activeFridgeId

    private val _activeFridgeName = MutableLiveData<String?>(null)
    val activeFridgeName: LiveData<String?> = _activeFridgeName

    private val _unreadCount = MutableLiveData<Int>(0)
    val unreadCount: LiveData<Int> = _unreadCount

    private val _lastScannedAt = MutableLiveData<String?>(null)
    val lastScannedAt: LiveData<String?> = _lastScannedAt
    private var chatOpen: Boolean = false

    private val unreadBumpListener = Emitter.Listener { args ->
        val payload = args.firstOrNull() as? JSONObject ?: return@Listener
        val incomingFridgeId = payload.optString("fridgeId")
        if (incomingFridgeId.isBlank()) return@Listener
        if (incomingFridgeId != _activeFridgeId.value) return@Listener
        if (chatOpen) return@Listener
        _unreadCount.postValue((_unreadCount.value ?: 0) + 1)
    }

    private val _members = MutableLiveData<Map<String, FridgeMemberDetailDto>>(emptyMap())
    val members: LiveData<Map<String, FridgeMemberDetailDto>> = _members

    private val _ownerAssignMessage = MutableLiveData<String?>(null)
    val ownerAssignMessage: LiveData<String?> = _ownerAssignMessage

    private var ownerChangeSocketJob: Job? = null

    init {
        SocketManager.get().on("fridgeChatUnread", unreadBumpListener)
        startOwnerChangeListener()
    }

    fun clearError() {
        _error.value = null
    }

    fun setChatOpen(open: Boolean) {
        chatOpen = open
        if (open) {
            _unreadCount.value = 0
        } else {
            refreshUnreadCount()
        }
    }

    fun assignOwner(itemId: String, newOwnerId: String?) {
        val fridgeId = _activeFridgeId.value ?: return
        val newOwnerName = newOwnerId?.let { _members.value?.get(it)?.displayName }
        viewModelScope.launch {
            val success = itemRepository.assignOwner(fridgeId, itemId, newOwnerId)
            _ownerAssignMessage.value = when {
                !success -> getApplication<Application>().getString(R.string.owner_assign_failed)
                newOwnerName != null -> getApplication<Application>().getString(R.string.owner_assigned_format, newOwnerName)
                else -> getApplication<Application>().getString(R.string.owner_removed)
            }
            if (success) loadItems()
        }
    }

    fun consumeOwnerAssignMessage() {
        _ownerAssignMessage.value = null
    }

    private fun loadMembers() {
        viewModelScope.launch {
            when (val result = fridgeRepository.getMembers()) {
                is FridgeResult.Success -> _members.value = result.data.associateBy { it.userId }
                is FridgeResult.Error -> {
                    val cached = fridgeRepository.getCachedMembers()
                    if (cached.isNotEmpty()) {
                        _members.value = cached.associateBy { it.userId }
                    }
                }
                else -> {}
            }
        }
    }

    private fun startOwnerChangeListener() {
        ownerChangeSocketJob?.cancel()
        ownerChangeSocketJob = viewModelScope.launch {
            while (isActive) {
                itemRepository.observeOwnerChanges().collect { event ->
                    if (event.fridgeId == _activeFridgeId.value) loadItems()
                }
                if (isActive) delay(2000)
            }
        }
    }

    fun refreshUnreadCount() {
        val fridgeId = _activeFridgeId.value ?: return
        viewModelScope.launch {
            when (val result = chatRepository.getUnreadCount(fridgeId)) {
                is ChatResult.Success -> _unreadCount.value = result.data
                is ChatResult.Error -> { /* keep previous value */ }
            }
        }
    }

    override fun onCleared() {
        SocketManager.get().off("fridgeChatUnread", unreadBumpListener)
        ownerChangeSocketJob?.cancel()
        super.onCleared()
    }

    fun loadItems() {
        if (!ApiClient.getTokenManager().isLoggedIn) {
            _state.value = State.NotLoggedIn
            return
        }
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val cached = itemRepository.getCachedItems()
            if (cached.isNotEmpty()) {
                _state.value = State.Items(buildFridgeItemList(cached))
            } else {
                _state.value = State.Loading
            }

            val fridgeResult = fridgeRepository.getMyFridge()
            
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1500) delay(1500 - elapsed)

            when (fridgeResult) {
                is FridgeResult.NoFridge -> {
                    itemRepository.clearCache()
                    _activeFridgeId.value = null
                    _activeFridgeName.value = null
                    _lastScannedAt.value = null
                    _unreadCount.value = 0
                    _state.value = State.NoFridge
                }
                is FridgeResult.Error -> {
                    _error.value = fridgeResult.message
                    if (cached.isEmpty()) {
                        _state.value = State.Error(fridgeResult.message)
                    }
                    if (_activeFridgeId.value == null) {
                        fridgeRepository.getCachedFridge()?.let { 
                            _activeFridgeId.value = it.id
                            _activeFridgeName.value = it.name
                        }
                    }
                }
                is FridgeResult.Success -> {
                    _activeFridgeId.value = fridgeResult.data.id
                    _activeFridgeName.value = fridgeResult.data.name
                    _lastScannedAt.value = fridgeResult.data.lastScannedAt
                    when (val itemResult = itemRepository.getItems(fridgeResult.data.id)) {
                        is FridgeResult.Success -> {
                            val items = itemResult.data
                            _state.value = if (items.isEmpty()) State.Empty
                            else State.Items(buildFridgeItemList(items))
                        }
                        is FridgeResult.Error -> {
                            _error.value = itemResult.message
                            if (cached.isEmpty()) {
                                _state.value = State.Error(itemResult.message)
                            }
                        }
                        else -> {}
                    }
                    refreshUnreadCount()
                    loadMembers()
                }
            }
        }
    }

    private fun buildFridgeItemList(items: List<InventoryItemDto>): List<FridgeItem> {
        val result = mutableListOf<FridgeItem>()
        _lastScannedAt.value?.let {
            result.add(FridgeItem.LastScanned(it))
        }
        val lowItems = items.filter { it.isRunningLow }
        if (lowItems.isNotEmpty()) {
            result.add(FridgeItem.RunningLow(lowItems.map { Pair(it.name, it.quantity) }))
        }

        // Group by category, similar to web frontend
        val grouped = items.groupBy { it.category ?: "Other" }
        grouped.keys.sorted().forEach { category ->
            result.add(FridgeItem.CategoryHeader(category))
            grouped[category]?.forEach { item ->
                result.add(
                    FridgeItem.Product(
                        item.id,
                        item.name,
                        item.quantity,
                        item.category,
                        item.isRunningLow,
                        item.ownerId
                    )
                )
            }
        }
        return result
    }
}
