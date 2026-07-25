package com.project.fridgemate.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.model.Notification
import com.project.fridgemate.data.model.NotificationType
import com.project.fridgemate.data.repository.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    data class PendingFridgeChat(val fridgeId: String, val fridgeName: String)

    private val _pendingFridgeChat = MutableLiveData<PendingFridgeChat?>(null)
    val pendingFridgeChat: LiveData<PendingFridgeChat?> = _pendingFridgeChat

    private val _pendingUserProfileId = MutableLiveData<String?>(null)
    val pendingUserProfileId: LiveData<String?> = _pendingUserProfileId

    private val _pendingSettingsOpen = MutableLiveData<Unit?>(null)
    val pendingSettingsOpen: LiveData<Unit?> = _pendingSettingsOpen

    private var socketJob: Job? = null
    private var updatedJob: Job? = null
    private var removedJob: Job? = null

    init {
        loadUnreadCount()
        startSocketListener()
        startUpdatedListener()
        startRemovedListener()
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
            repo.getUnreadCount()
                .onSuccess { _unreadCount.value = it }
        }
    }

    fun loadAndMarkAllAsRead() {
        _isLoading.value = true
        viewModelScope.launch {
            val loaded = repo.getNotifications().getOrNull()
            if (loaded != null) {
                _notifications.value = loaded.map { it.copy(isRead = true) }
                _unreadCount.value = 0
            }
            _isLoading.value = false
            repo.markAllAsRead()
        }
    }

    fun markAsRead(id: String) {
        val current = _notifications.value.orEmpty()
        val target = current.firstOrNull { it.id == id } ?: return
        if (target.isRead) return

        _notifications.value = current.map { if (it.id == id) it.copy(isRead = true) else it }
        _unreadCount.value = ((_unreadCount.value ?: 0) - 1).coerceAtLeast(0)

        viewModelScope.launch { repo.markAsRead(id) }
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

    fun requestNavToFridgeChat(fridgeId: String, fridgeName: String) {
        _pendingFridgeChat.value = PendingFridgeChat(fridgeId, fridgeName)
    }

    fun consumePendingFridgeChat() {
        _pendingFridgeChat.value = null
    }

    fun requestNavToUserProfile(userId: String) {
        _pendingUserProfileId.value = userId
    }

    fun consumePendingUserProfile() {
        _pendingUserProfileId.value = null
    }

    fun requestNavToSettings() {
        _pendingSettingsOpen.value = Unit
    }

    fun consumePendingSettingsOpen() {
        _pendingSettingsOpen.value = null
    }

    /**
     * Single source of truth for what tapping a notification does, used by both the
     * notification list and the real-time Dashboard banner. Returns true if it navigated
     * somewhere, so callers can decide whether to also dismiss their own UI.
     */
    fun handleNotificationClick(notification: Notification): Boolean {
        val handled = when (notification.type) {
            NotificationType.POST_LIKE, NotificationType.POST_COMMENT -> {
                val postId = notification.relatedId
                if (postId != null) {
                    requestNavToPost(postId)
                    true
                } else {
                    false
                }
            }
            NotificationType.CHAT_MESSAGE -> {
                val fridgeId = notification.relatedId
                if (fridgeId != null) {
                    requestNavToFridgeChat(fridgeId, notification.relatedLabel.orEmpty())
                    true
                } else {
                    false
                }
            }
            NotificationType.FOLLOW -> {
                val followerId = notification.relatedId
                if (followerId != null) {
                    requestNavToUserProfile(followerId)
                    true
                } else {
                    false
                }
            }
            NotificationType.FRIDGE_INVITE -> {
                requestNavToSettings()
                true
            }
            else -> false
        }
        if (handled) markAsRead(notification.id)
        return handled
    }

    private fun startSocketListener() {
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            while (isActive) {
                repo.observeNewNotifications().collect { notification ->
                    val current = _notifications.value.orEmpty()
                    val existing = current.firstOrNull { it.id == notification.id }
                    val wasUnread = existing?.isRead == false
                    val filtered = if (existing != null) current.filter { it.id != notification.id } else current
                    _notifications.value = listOf(notification) + filtered
                    if (!wasUnread) {
                        _unreadCount.value = (_unreadCount.value ?: 0) + 1
                    }
                    _incomingNotification.value = notification
                }
                // Flow closed because the socket disconnected (e.g. token refresh
                // caused SocketManager to replace the socket). Wait briefly so the
                // new socket has time to connect, then re-attach the listener.
                if (isActive) delay(2000)
            }
        }
    }

    private fun startUpdatedListener() {
        updatedJob?.cancel()
        updatedJob = viewModelScope.launch {
            while (isActive) {
                repo.observeUpdatedNotifications().collect { notification ->
                    val current = _notifications.value.orEmpty()
                    val existing = current.firstOrNull { it.id == notification.id }
                    val wasUnread = existing?.isRead == false
                    val filtered = if (existing != null) current.filter { it.id != notification.id } else current
                    _notifications.value = listOf(notification) + filtered
                    if (!wasUnread) {
                        _unreadCount.value = (_unreadCount.value ?: 0) + 1
                    }
                }
                if (isActive) delay(2000)
            }
        }
    }

    private fun startRemovedListener() {
        removedJob?.cancel()
        removedJob = viewModelScope.launch {
            while (isActive) {
                repo.observeRemovedNotifications().collect { removedId ->
                    val current = _notifications.value.orEmpty()
                    val target = current.firstOrNull { it.id == removedId } ?: return@collect
                    _notifications.value = current.filter { it.id != removedId }
                    if (!target.isRead) {
                        _unreadCount.value = ((_unreadCount.value ?: 0) - 1).coerceAtLeast(0)
                    }
                }
                if (isActive) delay(2000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketJob?.cancel()
        updatedJob?.cancel()
        removedJob?.cancel()
    }
}
