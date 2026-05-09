package com.project.fridgemate.data.repository

import com.google.gson.Gson
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.FridgeChatApi
import com.project.fridgemate.data.remote.dto.ChatMessageDto
import com.project.fridgemate.data.remote.socket.SocketManager
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

sealed class ChatResult<out T> {
    data class Success<T>(val data: T) : ChatResult<T>()
    data class Error(val message: String) : ChatResult<Nothing>()
}

data class ChatPage(val items: List<ChatMessageDto>, val hasMore: Boolean)

class FridgeChatRepository {

    private val api: FridgeChatApi = ApiClient.createApi(FridgeChatApi::class.java)
    private val gson = Gson()

    suspend fun getMessages(
        fridgeId: String,
        before: String? = null,
        limit: Int = 50,
    ): ChatResult<ChatPage> {
        return try {
            val response = api.getMessages(fridgeId, before, limit)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ChatResult.Success(ChatPage(body.items, body.hasMore))
            } else {
                ChatResult.Error(response.errorBody()?.string() ?: "Failed to load messages")
            }
        } catch (e: Exception) {
            ChatResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    fun observeMessages(fridgeId: String): Flow<ChatEvent> = callbackFlow {
        val socket = SocketManager.connect()

        val onJoined = Emitter.Listener { trySend(ChatEvent.Joined) }
        val onMessage = Emitter.Listener { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@Listener
            // Server sends { fridgeId, message }. Filter to this room only.
            val incomingFridgeId = payload.optString("fridgeId")
            if (incomingFridgeId != fridgeId) return@Listener
            val messageJson = payload.optJSONObject("message") ?: return@Listener
            val dto = runCatching {
                gson.fromJson(messageJson.toString(), ChatMessageDto::class.java)
            }.getOrNull() ?: return@Listener
            trySend(ChatEvent.MessageReceived(dto))
        }
        val onError = Emitter.Listener { args ->
            val payload = args.firstOrNull() as? JSONObject
            val msg = payload?.optString("message") ?: "Chat error"
            trySend(ChatEvent.Error(msg))
        }
        val onConnect = Emitter.Listener {
            socket.emit("joinFridgeChat", JSONObject().put("fridgeId", fridgeId))
        }
        val onDisconnect = Emitter.Listener { trySend(ChatEvent.Disconnected) }

        socket.on("fridgeChatJoined", onJoined)
        socket.on("fridgeMessageReceived", onMessage)
        socket.on("fridgeChatError", onError)
        socket.on(Socket.EVENT_CONNECT, onConnect)
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect)

        if (socket.connected()) {
            socket.emit("joinFridgeChat", JSONObject().put("fridgeId", fridgeId))
        }

        awaitClose {
            socket.emit("leaveFridgeChat", JSONObject().put("fridgeId", fridgeId))
            socket.off("fridgeChatJoined", onJoined)
            socket.off("fridgeMessageReceived", onMessage)
            socket.off("fridgeChatError", onError)
            socket.off(Socket.EVENT_CONNECT, onConnect)
            socket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        }
    }

    suspend fun markRead(fridgeId: String): ChatResult<Unit> {
        return try {
            val response = api.markRead(fridgeId)
            if (response.isSuccessful) {
                ChatResult.Success(Unit)
            } else {
                ChatResult.Error(response.errorBody()?.string() ?: "Failed to mark read")
            }
        } catch (e: Exception) {
            ChatResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun getUnreadCount(fridgeId: String): ChatResult<Int> {
        return try {
            val response = api.getUnreadCount(fridgeId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ChatResult.Success(body.unreadCount)
            } else {
                ChatResult.Error(response.errorBody()?.string() ?: "Failed to load unread count")
            }
        } catch (e: Exception) {
            ChatResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    fun sendMessage(fridgeId: String, content: String) {
        val socket = SocketManager.connect()
        val payload = JSONObject()
            .put("fridgeId", fridgeId)
            .put("content", content)
        socket.emit("sendFridgeMessage", payload)
    }

    fun sendRecipeShare(fridgeId: String, snapshot: RecipeSharePayload) {
        val socket = SocketManager.connect()
        val payload = JSONObject()
            .put("recipeId", snapshot.recipeId)
            .put("title", snapshot.title)
            .put("imageUrl", snapshot.imageUrl ?: JSONObject.NULL)
            .put("cookingTime", snapshot.cookingTime ?: JSONObject.NULL)
            .put("difficulty", snapshot.difficulty ?: JSONObject.NULL)
        val envelope = JSONObject()
            .put("fridgeId", fridgeId)
            .put("type", "recipe_share")
            .put("payload", payload)
        socket.emit("sendFridgeMessage", envelope)
    }
}

data class RecipeSharePayload(
    val recipeId: String,
    val title: String,
    val imageUrl: String?,
    val cookingTime: String?,
    val difficulty: String?,
)

sealed class ChatEvent {
    data object Joined : ChatEvent()
    data object Disconnected : ChatEvent()
    data class MessageReceived(val message: ChatMessageDto) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}
