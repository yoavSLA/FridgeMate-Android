package com.project.fridgemate.data.repository

import android.content.Context
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.JournalEntity
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.JournalApi
import com.project.fridgemate.data.remote.dto.CreateJournalRequest
import com.project.fridgemate.data.remote.dto.JournalEntryDto
import com.project.fridgemate.data.remote.dto.JournalListResponse
import com.project.fridgemate.data.remote.dto.JournalResponse
import com.project.fridgemate.data.remote.dto.UpdateJournalRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class JournalRepository(context: Context) {

    private val api: JournalApi = ApiClient.getJournalApi()
    private val dao = AppDatabase.getInstance(context).journalDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun getJournals(): FridgeResult<JournalListResponse> {
        return try {
            val response = api.getJournals(1, 100) // fetch latest 100
            if (response.isSuccessful) {
                val data = response.body()!!
                // cache
                val entities = data.items.map { it.toEntity() }
                dao.clearAll()
                dao.insertAll(entities)
                FridgeResult.Success(data)
            } else {
                fetchFromCache(response.errorBody()?.string())
            }
        } catch (e: Exception) {
            fetchFromCache(networkErrorMessage(e))
        }
    }

    suspend fun createJournal(request: CreateJournalRequest): FridgeResult<JournalEntryDto> {
        return try {
            val response = api.createJournal(request)
            if (response.isSuccessful) {
                val entry = response.body()!!.data
                dao.insert(entry.toEntity())
                FridgeResult.Success(entry)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun updateJournal(id: String, request: UpdateJournalRequest): FridgeResult<JournalEntryDto> {
        return try {
            val response = api.updateJournal(id, request)
            if (response.isSuccessful) {
                val entry = response.body()!!.data
                dao.insert(entry.toEntity())
                FridgeResult.Success(entry)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun deleteJournal(id: String): FridgeResult<Unit> {
        return try {
            val response = api.deleteJournal(id)
            if (response.isSuccessful) {
                dao.deleteById(id)
                FridgeResult.Success(Unit)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun uploadImage(imageBytes: ByteArray, mimeType: String): FridgeResult<String> {
        return try {
            val requestBody = imageBytes.toRequestBody(mimeType.toMediaType())
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val part = MultipartBody.Part.createFormData("image", "journal.$extension", requestBody)

            val response = api.uploadImage(part)
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()!!.data.imageUrl)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    private suspend fun fetchFromCache(errorMsg: String?): FridgeResult<JournalListResponse> {
        val cached = dao.getAll()
        return if (cached.isNotEmpty()) {
            val dtos = cached.map { it.toDto() }
            FridgeResult.Success(JournalListResponse(dtos, dtos.size, 1, 1))
        } else {
            FridgeResult.Error(parseError(errorMsg))
        }
    }

    private fun JournalEntryDto.toEntity(): JournalEntity {
        val meal = meals.firstOrNull()
        val time = try {
            dateFormat.parse(date)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        return JournalEntity(
            id = id,
            title = title,
            content = content ?: "",
            date = time,
            mealType = meal?.mealType ?: "",
            calories = meal?.calories?.toString() ?: "",
            macros = meal?.notes ?: "",
            mood = mood ?: "",
            imageUrl = imageUrl ?: ""
        )
    }

    private fun JournalEntity.toDto(): JournalEntryDto {
        val isoDate = dateFormat.format(Date(date))
        return JournalEntryDto(
            id = id,
            userId = "",
            title = title,
            content = content,
            date = isoDate,
            meals = listOf(
                com.project.fridgemate.data.remote.dto.JournalMealDto(
                    mealType = mealType,
                    recipeId = null,
                    customRecipeTitle = null,
                    calories = calories.toIntOrNull(),
                    notes = macros
                )
            ),
            rating = null,
            mood = mood,
            imageUrl = imageUrl,
            createdAt = isoDate,
            updatedAt = isoDate
        )
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
}
