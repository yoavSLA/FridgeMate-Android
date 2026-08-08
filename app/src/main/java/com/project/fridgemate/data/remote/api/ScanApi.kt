package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.ApiOkResponse
import com.project.fridgemate.data.remote.dto.PaginatedResponse
import com.project.fridgemate.data.remote.dto.ScanDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ScanApi {

    @Multipart
    @POST("fridges/me/scans")
    suspend fun uploadScan(
        @Part image: MultipartBody.Part
    ): Response<ApiOkResponse<ScanDto>>

    @GET("fridges/me/scans")
    suspend fun getScans(
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<ScanDto>>

    @GET("fridges/me/scans/{scanId}")
    suspend fun getScan(
        @Path("scanId") scanId: String
    ): Response<ApiOkResponse<ScanDto>>
}
