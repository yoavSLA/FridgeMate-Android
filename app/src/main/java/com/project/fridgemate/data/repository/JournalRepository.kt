package com.project.fridgemate.data.repository

import android.content.Context
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.JournalEntity
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.JournalApi
import com.project.fridgemate.data.remote.dto.CreateJournalRequest
import com.project.fridgemate.data.remote.dto.JournalEntryDto
import com.project.fridgemate.data.remote.dto.JournalListResponse
import com.project.fridgemate.data.remote.dto.UpdateJournalRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class JournalRepository(context: Context) : BaseRepository() {

    private val api: JournalApi = ApiClient.getJournalApi()
    private val dao = AppDatabase.getInstance(context).journalDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun getJournals(): FridgeResult<JournalListResponse> {
        return try {
            val response = api.getJournals(1, 100) // fetch latest 100
            if (response.isSuccessful) {
                val data = response.body() ?: return FridgeResult.Error("Empty response from server")
                val entities = data.items?.mapNotNull { dto ->
                    try {
                        dto.toEntity()
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()

                if (entities.isNotEmpty() || ((data.items != null) && data.items.isEmpty())) {
                    dao.clearAll()
                    dao.insertAll(entities)
                }
                
                FridgeResult.Success(data)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (_: Exception) {
            FridgeResult.Error(networkErrorMessage(Exception()))
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
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (_: Exception) {
            FridgeResult.Error(networkErrorMessage(Exception()))
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
        } catch (_: Exception) {
            FridgeResult.Error(networkErrorMessage(Exception()))
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
        } catch (_: Exception) {
            FridgeResult.Error(networkErrorMessage(Exception()))
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
        } catch (_: Exception) {
            FridgeResult.Error(networkErrorMessage(Exception()))
        }
    }



    private fun JournalEntryDto.toEntity(): JournalEntity {
        val meal = meals?.firstOrNull()
        val time = try {
            if (date.isNullOrEmpty()) System.currentTimeMillis()
            else dateFormat.parse(date)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
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
                else if (it.isJsonObject) it.asJsonObject["id"]?.asString ?: it.asJsonObject["_id"]?.asString
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


}
