package com.project.fridgemate.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.UserEntity
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.UserApi
import com.project.fridgemate.data.remote.dto.AddressDto
import com.project.fridgemate.data.remote.dto.UpdateProfileRequest
import com.project.fridgemate.data.remote.dto.UserDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.project.fridgemate.data.remote.dto.FcmTokenRequest
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
        email = email,
        displayName = displayName,
        userName = userName,
        profileImage = profileImage,
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
            email = email,
            displayName = displayName,
            userName = userName,
            profileImage = profileImage,
            role = role,
            allergies = allergiesList,
            dietPreference = dietPreference,
            activeFridgeId = activeFridgeId,
            address = addressDto
        )
    }
    suspend fun registerFcmToken(token: String): Result<Unit> {
        return try {
            val response = api.registerFcmToken(FcmTokenRequest(token))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to register FCM token: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
