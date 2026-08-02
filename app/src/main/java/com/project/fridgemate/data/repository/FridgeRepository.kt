package com.project.fridgemate.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.FridgeEntity
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.FridgeApi
import com.project.fridgemate.data.remote.dto.CreateFridgeRequest
import com.project.fridgemate.data.remote.dto.FridgeDto
import com.project.fridgemate.data.remote.dto.FridgeMemberDetailDto
import com.project.fridgemate.data.remote.dto.JoinFridgeRequest

sealed class FridgeResult<out T> {
    data class Success<T>(val data: T) : FridgeResult<T>()
    data class Error(val message: String) : FridgeResult<Nothing>()
    data object NoFridge : FridgeResult<Nothing>()
}

sealed class LastKnownFridge {
    data object Unknown : LastKnownFridge()
    data object None : LastKnownFridge()
    data class Present(val fridge: FridgeDto) : LastKnownFridge()
}

class FridgeRepository(context: Context) : BaseRepository() {

    private val fridgeApi: FridgeApi = ApiClient.createApi(FridgeApi::class.java)
    private val fridgeDao = AppDatabase.getInstance(context).fridgeDao()
    private val gson = Gson()

    companion object {
        @Volatile
        private var lastKnown: LastKnownFridge = LastKnownFridge.Unknown

        fun invalidate() {
            lastKnown = LastKnownFridge.Unknown
        }
    }

    suspend fun getMyFridge(): FridgeResult<FridgeDto> {
        return try {
            val response = fridgeApi.getMyFridge()
            if (response.isSuccessful) {
                val data = response.body()!!.data
                cacheFridge(data)
                lastKnown = LastKnownFridge.Present(data)
                FridgeResult.Success(data)
            } else if (response.code() == 404) {
                try { fridgeDao.clear() } catch (_: Exception) { }
                lastKnown = LastKnownFridge.None
                FridgeResult.NoFridge
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun getCachedFridge(): FridgeDto? {
        return loadCachedFridge()?.data
    }

    suspend fun peekLastKnownFridge(): LastKnownFridge {
        val snapshot = lastKnown
        if (snapshot != LastKnownFridge.Unknown) return snapshot
        val cached = loadCachedFridge() ?: return LastKnownFridge.Unknown
        val state = LastKnownFridge.Present(cached.data)
        lastKnown = state
        return state
    }

    suspend fun getMembers(): FridgeResult<List<FridgeMemberDetailDto>> {
        return try {
            val response = fridgeApi.getMyFridgeMembers()
            if (response.isSuccessful) {
                val members = response.body()!!.items
                cacheMembers(members)
                FridgeResult.Success(members)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun getCachedMembers(): List<FridgeMemberDetailDto> {
        return loadCachedMembers()?.data ?: emptyList()
    }

    suspend fun createFridge(name: String): FridgeResult<Unit> {
        return try {
            val response = fridgeApi.createFridge(CreateFridgeRequest(name.trim()))
            if (response.isSuccessful) {
                lastKnown = LastKnownFridge.Unknown
                FridgeResult.Success(Unit)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun joinFridge(inviteCode: String): FridgeResult<Unit> {
        return try {
            val response = fridgeApi.joinFridge(JoinFridgeRequest(inviteCode.trim().uppercase()))
            if (response.isSuccessful) {
                lastKnown = LastKnownFridge.Unknown
                FridgeResult.Success(Unit)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun leaveFridge(): FridgeResult<Unit> {
        return try {
            val response = fridgeApi.leaveFridge()
            if (response.isSuccessful) {
                try { fridgeDao.clear() } catch (_: Exception) { }
                lastKnown = LastKnownFridge.None
                FridgeResult.Success(Unit)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    private suspend fun cacheFridge(fridge: FridgeDto) {
        try {
            fridgeDao.clear()
            fridgeDao.insert(
                FridgeEntity(
                    id = fridge.id,
                    name = fridge.name,
                    inviteCode = fridge.inviteCode
                )
            )
        } catch (_: Exception) { }
    }

    private suspend fun cacheMembers(members: List<FridgeMemberDetailDto>) {
        try {
            val entity = fridgeDao.get() ?: return
            fridgeDao.insert(entity.copy(membersJson = gson.toJson(members)))
        } catch (_: Exception) { }
    }

    private suspend fun loadCachedFridge(): FridgeResult.Success<FridgeDto>? {
        return try {
            val entity = fridgeDao.get() ?: return null
            FridgeResult.Success(
                FridgeDto(
                    id = entity.id,
                    name = entity.name,
                    inviteCode = entity.inviteCode,
                    members = emptyList()
                )
            )
        } catch (_: Exception) { null }
    }

    private suspend fun loadCachedMembers(): FridgeResult.Success<List<FridgeMemberDetailDto>>? {
        return try {
            val entity = fridgeDao.get() ?: return null
            val type = object : TypeToken<List<FridgeMemberDetailDto>>() {}.type
            val members: List<FridgeMemberDetailDto> = gson.fromJson(entity.membersJson, type) ?: emptyList()
            if (members.isNotEmpty()) FridgeResult.Success(members) else null
        } catch (_: Exception) { null }
    }


}
