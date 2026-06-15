package com.project.fridgemate.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.model.Notification
import com.project.fridgemate.data.repository.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotificationViewModel : ViewModel() {

    private val repo = NotificationRepository()

    private val _notifications = MutableLiveData<List<Notification>>(emptyList())
    val notifications: LiveData<List<Notification>> = _notifications

    private val _unreadCount = MutableLiveData(0)
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _incomingNotification = MutableLiveData<Notification?>(null)
    val incomingNotification: LiveData<Notification?> = _incomingNotification

    private val _pendingPostId = MutableLiveData<String?>(null)
    val pendingPostId: LiveData<String?> = _pendingPostId

    private var socketJob: Job? = null

    init {
        loadUnreadCount()
        startSocketListener()
    }

    fun loadNotifications() {
        _isLoading.value = true
        viewModelScope.launch {
            repo.getNotifications()
                .onSuccess { _notifications.value = it }
                .onFailure { }
            _isLoading.value = false
        }
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
            repo.getUnreadCount()
                .onSuccess { _unreadCount.value = it }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repo.markAllAsRead().onSuccess {
                _unreadCount.value = 0
                _notifications.value = _notifications.value?.map { it.copy(isRead = true) }
            }
        }
    }

    fun consumeIncoming() {
        _incomingNotification.value = null
    }

    fun requestNavToPost(postId: String) {
        _pendingPostId.value = postId
    }

    fun consumePendingPostId() {
        _pendingPostId.value = null
    }

    private fun startSocketListener() {
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            while (isActive) {
                repo.observeNewNotifications().collect { notification ->
                    _unreadCount.postValue((_unreadCount.value ?: 0) + 1)
                    _incomingNotification.postValue(notification)
                    val current = _notifications.value.orEmpty()
                    _notifications.postValue(listOf(notification) + current)
                }
                // Flow closed because the socket disconnected (e.g. token refresh
                // caused SocketManager to replace the socket). Wait briefly so the
                // new socket has time to connect, then re-attach the listener.
                if (isActive) delay(2000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketJob?.cancel()
    }
}
