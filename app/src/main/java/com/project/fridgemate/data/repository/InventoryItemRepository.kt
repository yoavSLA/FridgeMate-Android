package com.project.fridgemate.data.repository

import android.content.Context
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.InventoryItemEntity
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.InventoryItemApi
import com.project.fridgemate.data.remote.dto.InventoryItemDto
import com.project.fridgemate.data.remote.dto.ItemOwnerChangedDto
import com.project.fridgemate.data.remote.socket.SocketManager
import com.google.gson.Gson
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class InventoryItemRepository(context: Context) {

    private val api = ApiClient.createApi(InventoryItemApi::class.java)
    private val dao = AppDatabase.getInstance(context).inventoryItemDao()
    private val gson = Gson()

    suspend fun getCachedItems(): List<InventoryItemDto> {
        return dao.getAll().map { it.toDto() }
    }

    suspend fun getItems(fridgeId: String): List<InventoryItemDto> {
        return try {
            val response = api.getItems(fridgeId)
            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                cacheItems(items)
                items
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ownerId = null unassigns the item. Built as raw JSON (not a Gson @Body) so the
    // explicit "ownerId": null survives — Gson's converter omits null fields by default.
    suspend fun assignOwner(fridgeId: String, itemId: String, ownerId: String?): Boolean {
        return try {
            val json = JSONObject().put("ownerId", ownerId ?: JSONObject.NULL).toString()
            val body = json.toRequestBody("application/json".toMediaType())
            api.assignOwner(fridgeId, itemId, body).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // Reuses the existing SocketManager — no new connection created
    fun observeOwnerChanges(): Flow<ItemOwnerChangedDto> = callbackFlow {
        val socket = SocketManager.connect()

        val onOwnerChanged = Emitter.Listener { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@Listener
            runCatching {
                gson.fromJson(json.toString(), ItemOwnerChangedDto::class.java)
            }.getOrNull()?.let { trySend(it) }
        }
        val onDisconnect = Emitter.Listener {
            if (SocketManager.connect() !== socket) close()
        }

        socket.on("itemOwnerChanged", onOwnerChanged)
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect)

        awaitClose {
            socket.off("itemOwnerChanged", onOwnerChanged)
            socket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        }
    }

    suspend fun clearCache() {
        try { dao.deleteAll() } catch (_: Exception) { }
    }

    private suspend fun cacheItems(items: List<InventoryItemDto>) {
        try {
            dao.deleteAll()
            dao.insertAll(items.map { it.toEntity() })
        } catch (_: Exception) { }
    }

    private fun InventoryItemDto.toEntity() = InventoryItemEntity(
        id = id,
        fridgeId = fridgeId,
        ownerId = ownerId,
        name = name,
        quantity = quantity,
        ownership = ownership,
        isRunningLow = isRunningLow
    )

    private fun InventoryItemEntity.toDto() = InventoryItemDto(
        id = id,
        fridgeId = fridgeId,
        ownerId = ownerId,
        name = name,
        quantity = quantity,
        ownership = ownership,
        isRunningLow = isRunningLow
    )
}
