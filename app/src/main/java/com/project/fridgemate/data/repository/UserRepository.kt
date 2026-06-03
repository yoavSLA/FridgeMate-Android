package com.project.fridgemate.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.UserEntity
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.UserApi
import com.project.fridgemate.data.remote.dto.AddressDto
import com.project.fridgemate.data.remote.dto.FollowToggleResponse
import com.project.fridgemate.data.remote.dto.UpdateProfileRequest
import com.project.fridgemate.data.remote.dto.UserDto
import com.project.fridgemate.data.remote.dto.UserListItemDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class UserRepository(context: Context) {

    private val api = ApiClient.createApi(UserApi::class.java)
    private val userDao = AppDatabase.getInstance(context).userDao()
    private val gson = Gson()

    suspend fun getCachedUser(id: String): UserDto? {
        return userDao.get(id)?.toDto()
    }

    suspend fun getUserById(id: String): UserDto? {
        val response = api.getUserById(id)
        return if (response.isSuccessful) {
            val dto = response.body()
            if (dto != null) cacheUser(dto)
            dto
        } else null
    }

    suspend fun updateProfile(id: String, request: UpdateProfileRequest): UserDto? {
        val response = api.updateProfile(id, request)
        return if (response.isSuccessful) {
            val dto = response.body()
            if (dto != null) cacheUser(dto)
            dto
        } else null
    }

    suspend fun searchUsers(q: String, page: Int = 1, limit: Int = 20): FridgeResult<List<UserListItemDto>> {
        return try {
            val response = api.searchUsers(q, page, limit)
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()?.items ?: emptyList())
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun toggleFollow(targetUserId: String): FridgeResult<FollowToggleResponse> {
        return try {
            val response = api.toggleFollow(targetUserId)
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()!!.data)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun getFollowers(userId: String, page: Int = 1, limit: Int = 20): FridgeResult<List<UserListItemDto>> {
        return try {
            val response = api.getFollowers(userId, page, limit)
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()?.items ?: emptyList())
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun getFollowing(userId: String, page: Int = 1, limit: Int = 20): FridgeResult<List<UserListItemDto>> {
        return try {
            val response = api.getFollowing(userId, page, limit)
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()?.items ?: emptyList())
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    private fun parseError(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Something went wrong. Please try again."
        return try {
            val json = org.json.JSONObject(errorBody)
            json.optString("message", "Something went wrong. Please try again.")
        } catch (_: Exception) {
            errorBody
        }
    }

    private fun networkErrorMessage(e: Exception): String {
        return if (e is java.net.ConnectException || e is java.net.UnknownHostException) {
            "Unable to connect to server. Please check your connection."
        } else {
            e.localizedMessage ?: "An unexpected error occurred."
        }
    }

    suspend fun uploadProfileImage(imageBytes: ByteArray, mimeType: String): String? {
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val requestBody = imageBytes.toRequestBody(mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("image", "profile.$extension", requestBody)
        val response = api.uploadProfileImage(part)
        return if (response.isSuccessful) response.body()?.data?.imageUrl else null
    }

    private suspend fun cacheUser(dto: UserDto) {
        try {
            userDao.insert(dto.toEntity())
        } catch (_: Exception) { }
    }

    private fun UserDto.toEntity() = UserEntity(
        id = id,
        email = email ?: "",
        displayName = displayName,
        userName = userName,
        profileImage = profileImage,
        bio = bio,
        role = role,
        allergiesJson = gson.toJson(allergies),
        dietPreference = dietPreference,
        activeFridgeId = activeFridgeId,
        addressJson = if (address != null) gson.toJson(address) else null
    )

    private fun UserEntity.toDto(): UserDto {
        val allergiesList: List<String> = try {
            gson.fromJson(allergiesJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val addressDto: AddressDto? = try {
            if (addressJson != null) gson.fromJson(addressJson, AddressDto::class.java) else null
        } catch (_: Exception) { null }
        return UserDto(
            id = id,
            email = email.ifEmpty { null },
            displayName = displayName,
            userName = userName,
            profileImage = profileImage,
            bio = bio,
            role = role,
            allergies = allergiesList,
            dietPreference = dietPreference,
            activeFridgeId = activeFridgeId,
            address = addressDto
        )
    }
}
