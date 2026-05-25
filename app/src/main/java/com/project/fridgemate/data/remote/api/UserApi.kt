package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.ApiOkResponse
import com.project.fridgemate.data.remote.dto.FcmTokenRequest
import com.project.fridgemate.data.remote.dto.FcmTokenResponse
import com.project.fridgemate.data.remote.dto.UpdateProfileRequest
import com.project.fridgemate.data.remote.dto.UploadImageResponse
import com.project.fridgemate.data.remote.dto.UserDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface UserApi {

    @GET("user/{id}")
    suspend fun getUserById(@Path("id") id: String): Response<UserDto>

    @PUT("user/{id}")
    suspend fun updateProfile(
        @Path("id") id: String,
        @Body request: UpdateProfileRequest
    ): Response<UserDto>

    @Multipart
    @POST("upload")
    suspend fun uploadProfileImage(
        @Part image: MultipartBody.Part
    ): Response<ApiOkResponse<UploadImageResponse>>

    @POST("user/fcm-token")
    suspend fun registerFcmToken(@Body request: FcmTokenRequest): Response<FcmTokenResponse>
}