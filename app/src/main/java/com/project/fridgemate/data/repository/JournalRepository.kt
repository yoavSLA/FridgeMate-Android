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
        android.util.Log.d("JournalRepository", "Fetching journals...")
        return try {
            val response = api.getJournals(1, 100) // fetch latest 100
            android.util.Log.d("JournalRepository", "API Response: ${response.code()} ${response.message()}")
            if (response.isSuccessful) {
                val data = response.body() ?: return FridgeResult.Error("Empty response from server")
                android.util.Log.d("JournalRepository", "API Response Data: $data")
                val entities = data.items?.mapNotNull { dto ->
                    try {
                        dto.toEntity()
                    } catch (e: Exception) {
                        android.util.Log.e("JournalRepository", "Failed to map entry ${dto.id ?: "unknown"}", e)
                        null
                    }
                } ?: emptyList()

                // Only clear and update cache if we managed to map something or it's a valid empty response
                if (entities.isNotEmpty() || (data.items != null && data.items.isEmpty())) {
                    dao.clearAll()
                    dao.insertAll(entities)
                }
                
                FridgeResult.Success(data)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = parseError(errorBody)
                android.util.Log.e("JournalRepository", "API Error ${response.code()}: $errorMsg | Body: $errorBody")
                FridgeResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            android.util.Log.e("JournalRepository", "Network/Parsing Exception", e)
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun getCachedJournals(): List<JournalEntryDto> {
        return try {
            dao.getAll().map { it.toDto() }
        } catch (_: Exception) {
            emptyList()
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
                val errorBody = response.errorBody()?.string()
                val errorMsg = parseError(errorBody)
                android.util.Log.e("JournalRepository", "Create Journal API Error ${response.code()}: $errorMsg")
                FridgeResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            android.util.Log.e("JournalRepository", "Create Journal Exception", e)
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
                val errorBody = response.errorBody()?.string()
                val errorMsg = parseError(errorBody)
                android.util.Log.e("JournalRepository", "Update Journal API Error ${response.code()}: $errorMsg")
                FridgeResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            android.util.Log.e("JournalRepository", "Update Journal Exception", e)
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
                val errorBody = response.errorBody()?.string()
                val errorMsg = parseError(errorBody)
                android.util.Log.e("JournalRepository", "Delete Journal API Error ${response.code()}: $errorMsg")
                FridgeResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            android.util.Log.e("JournalRepository", "Delete Journal Exception", e)
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
                val errorBody = response.errorBody()?.string()
                val errorMsg = parseError(errorBody)
                android.util.Log.e("JournalRepository", "Upload Image API Error ${response.code()}: $errorMsg")
                FridgeResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            android.util.Log.e("JournalRepository", "Upload Image Exception", e)
            FridgeResult.Error(networkErrorMessage(e))
        }
    }



    private fun JournalEntryDto.toEntity(): JournalEntity {
        val meal = meals?.firstOrNull()
        val time = try {
            if (date.isNullOrEmpty()) System.currentTimeMillis()
            else dateFormat.parse(date)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        
        return JournalEntity(
            id = id ?: java.util.UUID.randomUUID().toString(),
            title = title ?: "Untitled Entry",
            content = content ?: "",
            date = time,
            mealType = meal?.mealType ?: "",
            calories = meal?.calories?.toString() ?: "",
            macros = meal?.notes ?: "",
            mood = mood ?: "",
            imageUrl = imageUrl ?: "",
            recipeId = meal?.recipeId?.let { 
                if (it.isJsonPrimitive) it.asString 
                else if (it.isJsonObject) it.asJsonObject.get("id")?.asString ?: it.asJsonObject.get("_id")?.asString
                else it.toString() 
            }
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
                    recipeId = recipeId?.let { com.google.gson.JsonPrimitive(it) },
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
        if (errorBody.isNullOrBlank()) return "Empty error from server"
        return try {
            val json = org.json.JSONObject(errorBody)
            json.optString("message", errorBody)
        } catch (_: Exception) {
            errorBody ?: "Unknown error"
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
