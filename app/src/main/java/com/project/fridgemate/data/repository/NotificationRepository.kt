package com.project.fridgemate.data.repository

import com.google.gson.Gson
import com.project.fridgemate.data.model.Notification
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

class NotificationRepository {

    private val api = ApiClient.createApi(NotificationApi::class.java)
    private val gson = Gson()

    suspend fun getNotifications(): Result<List<Notification>> = runCatching {
        val response = api.getNotifications()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            body.notifications.map { it.toNotification() }
        } else {
            throw Exception("Failed to load notifications")
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
        val response = api.markAllAsRead()
        if (!response.isSuccessful) throw Exception("Failed to mark all as read")
    }

    // Reuses the existing SocketManager — no new connection created
    fun observeNewNotifications(): Flow<Notification> = callbackFlow {
        val socket = SocketManager.connect()

        val onNotification = Emitter.Listener { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@Listener
            runCatching {
                gson.fromJson(json.toString(), NotificationDto::class.java).toNotification()
            }.getOrNull()?.let { trySend(it) }
        }

        // Close the flow when the socket is disconnected so the ViewModel can reconnect
        // to a fresh socket (e.g. after a token refresh recreates the socket).
        val onDisconnect = Emitter.Listener { close() }

        socket.on("new_notification", onNotification)
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect)

        awaitClose {
            socket.off("new_notification", onNotification)
            socket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        }
    }
}
