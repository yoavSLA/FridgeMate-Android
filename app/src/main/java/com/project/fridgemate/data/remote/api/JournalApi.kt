package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.CreateJournalRequest
import com.project.fridgemate.data.remote.dto.JournalListResponse
import com.project.fridgemate.data.remote.dto.JournalResponse
import com.project.fridgemate.data.remote.dto.UpdateJournalRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface JournalApi {

    @GET("journal")
    suspend fun getJournals(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<JournalListResponse>

    @GET("journal/{id}")
    suspend fun getJournalById(@Path("id") id: String): Response<JournalResponse>

    @POST("journal")
    suspend fun createJournal(@Body request: CreateJournalRequest): Response<JournalResponse>

    @PUT("journal/{id}")
    suspend fun updateJournal(
        @Path("id") id: String,
        @Body request: UpdateJournalRequest
    ): Response<JournalResponse>

    @DELETE("journal/{id}")
    suspend fun deleteJournal(@Path("id") id: String): Response<Void>

    @Multipart
    @POST("upload")
    suspend fun uploadImage(@Part image: MultipartBody.Part): Response<com.project.fridgemate.data.remote.dto.ApiOkResponse<com.project.fridgemate.data.remote.dto.UploadImageResponse>>
}
