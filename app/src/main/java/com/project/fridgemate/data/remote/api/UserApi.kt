package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.ApiOkResponse
import com.project.fridgemate.data.remote.dto.FollowToggleResponse
import com.project.fridgemate.data.remote.dto.UpdateProfileRequest
import com.project.fridgemate.data.remote.dto.UploadImageResponse
import com.project.fridgemate.data.remote.dto.UserDto
import com.project.fridgemate.data.remote.dto.UserListResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("user/search")
    suspend fun searchUsers(
        @Query("q") q: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<UserListResponse>

    @POST("user/{id}/follow")
    suspend fun toggleFollow(
        @Path("id") targetId: String
    ): Response<ApiOkResponse<FollowToggleResponse>>

    @GET("user/{id}/followers")
    suspend fun getFollowers(
        @Path("id") userId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<UserListResponse>

    @GET("user/{id}/following")
    suspend fun getFollowing(
        @Path("id") userId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<UserListResponse>
}
