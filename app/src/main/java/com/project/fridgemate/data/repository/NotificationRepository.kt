package com.project.fridgemate.data.repository

import android.content.Context
import com.google.gson.Gson
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.NotificationEntity
import com.project.fridgemate.data.model.Notification
import com.project.fridgemate.data.model.NotificationType
import com.project.fridgemate.data.model.toNotification
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.NotificationApi
import com.project.fridgemate.data.remote.dto.NotificationDto
import com.project.fridgemate.data.remote.dto.NotificationUnreadCountResponse
import com.project.fridgemate.data.remote.socket.SocketManager
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

class NotificationRepository(context: Context) {

    private val api = ApiClient.createApi(NotificationApi::class.java)
    private val dao = AppDatabase.getInstance(context).notificationDao()
    private val gson = Gson()

    companion object {
        private const val MAX_CACHED = 100
    }

    suspend fun getNotifications(): Result<List<Notification>> = runCatching {
        val response = api.getNotifications()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            val notifications = body.notifications.map { it.toNotification() }
            replaceCache(notifications)
            notifications
        } else {
            throw Exception("Failed to load notifications")
        }
    }

    suspend fun getCachedNotifications(): List<Notification> {
        return try {
            dao.getAll().map { it.toNotification() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getUnreadCount(): Result<Int> = runCatching {
        val response = api.getUnreadCount()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            body.unreadCount
        } else {
            throw Exception("Failed to load unread count")
        }
    }

    suspend fun markAllAsRead(): Result<Unit> = runCatching {
        try { dao.markAllAsRead() } catch (_: Exception) { }
        val response = api.markAllAsRead()
        if (!response.isSuccessful) throw Exception("Failed to mark all as read")
    }

    suspend fun markAsRead(id: String): Result<Unit> = runCatching {
        try { dao.markAsRead(id) } catch (_: Exception) { }
        val response = api.markAsRead(id)
        if (!response.isSuccessful) throw Exception("Failed to mark notification as read")
    }

    suspend fun cacheNotification(notification: Notification) {
        try {
            dao.insert(notification.toEntity())
            dao.trimTo(MAX_CACHED)
        } catch (_: Exception) { }
    }

    suspend fun removeCachedNotification(id: String) {
        try { dao.deleteById(id) } catch (_: Exception) { }
    }

    private suspend fun replaceCache(notifications: List<Notification>) {
        try {
            dao.clearAll()
            dao.insertAll(notifications.map { it.toEntity() })
            dao.trimTo(MAX_CACHED)
        } catch (_: Exception) { }
    }

    private fun Notification.toEntity() = NotificationEntity(
        id = id,
        type = type.name,
        title = title,
        message = message,
        timestamp = timestamp,
        isRead = isRead,
        relatedId = relatedId,
        relatedLabel = relatedLabel
    )

    private fun NotificationEntity.toNotification() = Notification(
        id = id,
        type = runCatching { NotificationType.valueOf(type) }
            .getOrDefault(NotificationType.SYSTEM),
        title = title,
        message = message,
        timestamp = timestamp,
        isRead = isRead,
        relatedId = relatedId,
        relatedLabel = relatedLabel
    )

    fun observeNewNotifications(): Flow<Notification> = callbackFlow {
        val socket = SocketManager.connect()

        val onNotification = Emitter.Listener { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@Listener
            runCatching {
                gson.fromJson(json.toString(), NotificationDto::class.java).toNotification()
            }.getOrNull()?.let { trySend(it) }
        }

        val onDisconnect = Emitter.Listener {
            if (SocketManager.connect() !== socket) close()
        }

        socket.on("new_notification", onNotification)
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect)

        awaitClose {
            socket.off("new_notification", onNotification)
            socket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        }
    }

    fun observeUpdatedNotifications(): Flow<Notification> = callbackFlow {
        val socket = SocketManager.connect()

        val onUpdated = Emitter.Listener { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@Listener
            runCatching {
                gson.fromJson(json.toString(), NotificationDto::class.java).toNotification()
            }.getOrNull()?.let { trySend(it) }
        }

        val onDisconnect = Emitter.Listener {
            if (SocketManager.connect() !== socket) close()
        }

        socket.on("notification_updated", onUpdated)
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect)

        awaitClose {
            socket.off("notification_updated", onUpdated)
            socket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        }
    }

    fun observeRemovedNotifications(): Flow<String> = callbackFlow {
        val socket = SocketManager.connect()

        val onRemoved = Emitter.Listener { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@Listener
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return@Listener
            trySend(id)
        }

        val onDisconnect = Emitter.Listener {
            if (SocketManager.connect() !== socket) close()
        }

        socket.on("notification_removed", onRemoved)
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect)

        awaitClose {
            socket.off("notification_removed", onRemoved)
            socket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        }
    }
}
