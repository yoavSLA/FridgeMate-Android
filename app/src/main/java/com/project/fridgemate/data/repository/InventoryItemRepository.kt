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

class InventoryItemRepository(context: Context) : BaseRepository() {

    private val api = ApiClient.createApi(InventoryItemApi::class.java)
    private val dao = AppDatabase.getInstance(context).inventoryItemDao()
    private val gson = Gson()

    suspend fun getCachedItems(): List<InventoryItemDto> {
        return dao.getAll().map { it.toDto() }
    }

    /**
     * The fridge screen renders every item at once, so walk the paged endpoint to
     * completion — otherwise the server's default page size silently truncates
     * larger fridges.
     */
    suspend fun getItems(fridgeId: String, mineOrUnowned: Boolean = false): FridgeResult<List<InventoryItemDto>> {
        return try {
            val items = mutableListOf<InventoryItemDto>()
            var page = 1

            while (true) {
                val response = api.getItems(fridgeId, mineOrUnowned, page, PAGE_SIZE)
                if (!response.isSuccessful) {
                    return FridgeResult.Error("Failed to fetch items: ${response.code()}")
                }

                val body = response.body()
                val batch = body?.items ?: emptyList()
                items.addAll(batch)

                val total = body?.total ?: items.size
                if (batch.isEmpty() || items.size >= total || page >= MAX_PAGES) break
                page++
            }

            if (!mineOrUnowned) cacheItems(items)
            FridgeResult.Success(items)
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }



    suspend fun assignOwner(fridgeId: String, itemId: String, ownerId: String?): InventoryItemDto? {
        return try {
            val json = JSONObject().put("ownerId", ownerId ?: JSONObject.NULL).toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val response = api.assignOwner(fridgeId, itemId, body)
            if (response.isSuccessful) {
                val updatedItem = response.body()?.data
                if (updatedItem != null) {
                    dao.updateOwnerId(itemId, ownerId)
                }
                updatedItem
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun observeOwnerChanges(): Flow<ItemOwnerChangedDto> = callbackFlow {
        val socket = SocketManager.connect()

        val onOwnerChanged = Emitter.Listener { args ->
            val json = (args.firstOrNull() as? JSONObject) ?: return@Listener
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
        category = category,
        ownership = ownership,
        isRunningLow = isRunningLow,
        daysOfSupply = daysOfSupply,
        suggestedRestockQuantity = suggestedRestockQuantity,
        lowStockReason = lowStockReason
    )

    private fun InventoryItemEntity.toDto() = InventoryItemDto(
        id = id,
        fridgeId = fridgeId,
        ownerId = ownerId,
        name = name,
        quantity = quantity,
        category = category,
        ownership = ownership,
        isRunningLow = isRunningLow,
        daysOfSupply = daysOfSupply,
        suggestedRestockQuantity = suggestedRestockQuantity,
        lowStockReason = lowStockReason
    )

    companion object {
        private const val PAGE_SIZE = 100
        /** Stops a bad `total` from turning the fetch loop into an infinite one. */
        private const val MAX_PAGES = 20
    }
}
