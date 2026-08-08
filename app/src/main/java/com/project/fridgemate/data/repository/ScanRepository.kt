package com.project.fridgemate.data.repository

import android.content.Context
import com.google.gson.Gson
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.ScanEntity
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.ScanApi
import com.project.fridgemate.data.remote.dto.ScanDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ScanRepository(context: Context) : BaseRepository() {

    private val scanApi: ScanApi = ApiClient.getScanApi()
    private val dao = AppDatabase.getInstance(context).scanDao()
    private val gson = Gson()

    companion object {
        private const val MAX_CACHED_SCANS = 20
    }

    suspend fun uploadScan(imageBytes: ByteArray, mimeType: String): FridgeResult<ScanDto> {
        return try {
            val requestBody = imageBytes.toRequestBody(mimeType.toMediaType())
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val part = MultipartBody.Part.createFormData("image", "fridge.$extension", requestBody)

            val response = scanApi.uploadScan(part)
            if (response.isSuccessful) {
                val scan = response.body()!!.data
                cacheScans(listOf(scan))
                FridgeResult.Success(scan)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    /** A scan never changes once created, so a cache hit is always safe to reuse. */
    suspend fun getScan(scanId: String): FridgeResult<ScanDto> {
        getCachedScan(scanId)?.let { return FridgeResult.Success(it) }

        return try {
            val response = scanApi.getScan(scanId)
            if (response.isSuccessful) {
                val scan = response.body()!!.data
                cacheScans(listOf(scan))
                FridgeResult.Success(scan)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    /** Fallback for scan notifications that carry no scan id. */
    suspend fun getLatestScan(): FridgeResult<ScanDto> {
        getCachedLatestScan()?.let { return FridgeResult.Success(it) }

        return try {
            val response = scanApi.getScans(limit = 1)
            if (response.isSuccessful) {
                val scan = response.body()?.items?.firstOrNull()
                    ?: return FridgeResult.Error("No scans yet.")
                cacheScans(listOf(scan))
                FridgeResult.Success(scan)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    private suspend fun getCachedScan(scanId: String): ScanDto? {
        return try { dao.getById(scanId)?.toDto() } catch (_: Exception) { null }
    }

    private suspend fun getCachedLatestScan(): ScanDto? {
        return try { dao.getLatest()?.toDto() } catch (_: Exception) { null }
    }

    private suspend fun cacheScans(scans: List<ScanDto>) {
        try {
            dao.insertAll(scans.map { it.toEntity() })
            dao.trimTo(MAX_CACHED_SCANS)
        } catch (_: Exception) { }
    }

    private fun ScanDto.toEntity() = ScanEntity(
        id = id,
        fridgeId = fridgeId,
        createdAt = createdAt,
        scanJson = gson.toJson(this)
    )

    private fun ScanEntity.toDto(): ScanDto? {
        return try {
            gson.fromJson(scanJson, ScanDto::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
