package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.ForgotPasswordRequest
import com.project.fridgemate.data.remote.dto.GoogleLoginRequest
import com.project.fridgemate.data.remote.dto.LoginRequest
import com.project.fridgemate.data.remote.dto.LoginResponse
import com.project.fridgemate.data.remote.dto.MessageResponse
import com.project.fridgemate.data.remote.dto.RefreshTokenRequest
import com.project.fridgemate.data.remote.dto.RefreshTokenResponse
import com.project.fridgemate.data.remote.dto.RegisterRequest
import com.project.fridgemate.data.remote.dto.RegisterResponse
import com.project.fridgemate.data.remote.dto.ResetPasswordRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/login/google/android")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): Response<LoginResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("auth/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<MessageResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<MessageResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<MessageResponse>
}
