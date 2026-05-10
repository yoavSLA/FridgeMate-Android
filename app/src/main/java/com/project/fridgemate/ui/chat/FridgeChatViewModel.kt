package com.project.fridgemate.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.dto.ChatMessageDto
import com.project.fridgemate.data.repository.ChatEvent
import com.project.fridgemate.data.repository.ChatResult
import com.project.fridgemate.data.repository.FridgeChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FridgeChatViewModel : ViewModel() {

    private val repo = FridgeChatRepository()
    private val tokenManager = ApiClient.getTokenManager()

    val currentUserId: String? get() = tokenManager.userId

    private val _messages = MutableLiveData<List<ChatMessageDto>>(emptyList())
    val messages: LiveData<List<ChatMessageDto>> = _messages

    private val _initialLoading = MutableLiveData(false)
    val initialLoading: LiveData<Boolean> = _initialLoading

    private val _loadingOlder = MutableLiveData(false)
    val loadingOlder: LiveData<Boolean> = _loadingOlder

    private val _hasMore = MutableLiveData(false)
    val hasMore: LiveData<Boolean> = _hasMore

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var observeJob: Job? = null
    private var fridgeId: String? = null

    fun start(fridgeId: String) {
        if (this.fridgeId == fridgeId && observeJob?.isActive == true) return
        this.fridgeId = fridgeId

        loadInitial(fridgeId)

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repo.observeMessages(fridgeId).collect { event ->
                when (event) {
                    is ChatEvent.MessageReceived -> appendMessage(event.message)
                    is ChatEvent.Error -> _error.postValue(event.message)
                    ChatEvent.Joined, ChatEvent.Disconnected -> Unit
                }
            }
        }
    }

    private fun loadInitial(fridgeId: String) {
        _initialLoading.value = true
        viewModelScope.launch {
            when (val result = repo.getMessages(fridgeId)) {
                is ChatResult.Success -> {
                    _messages.value = result.data.items
                    _hasMore.value = result.data.hasMore
                }
                is ChatResult.Error -> _error.value = result.message
            }
            _initialLoading.value = false
            markRead()
        }
    }

    fun markRead() {
        val id = fridgeId ?: return
        viewModelScope.launch { repo.markRead(id) }
    }

    fun loadOlder() {
        val id = fridgeId ?: return
        if (_loadingOlder.value == true) return
        if (_hasMore.value != true) return
        val current = _messages.value.orEmpty()
        val before = current.firstOrNull()?.id ?: return

        _loadingOlder.value = true
        viewModelScope.launch {
            when (val result = repo.getMessages(id, before = before)) {
                is ChatResult.Success -> {
                    _messages.value = result.data.items + current
                    _hasMore.value = result.data.hasMore
                }
                is ChatResult.Error -> _error.value = result.message
            }
            _loadingOlder.value = false
        }
    }

    fun send(content: String) {
        val id = fridgeId ?: return
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        repo.sendMessage(id, trimmed)
    }

    fun consumeError() {
        _error.value = null
    }

    private fun appendMessage(message: ChatMessageDto) {
        val current = _messages.value.orEmpty()
        if (current.any { it.id == message.id }) return
        _messages.postValue(current + message)
        if (message.sender?.id != currentUserId) markRead()
    }
}
