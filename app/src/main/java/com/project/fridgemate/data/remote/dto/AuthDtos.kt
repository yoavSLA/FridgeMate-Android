package com.project.fridgemate.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Requests ────────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String
)

data class GoogleLoginRequest(
    val idToken: String
)

// ── Responses ───────────────────────────────────────────────────────────────

data class LoginResponse(
    val message: String,
    val accessToken: String,
    val refreshToken: String
)

data class RegisterResponse(
    val message: String,
    val user: UserDto
)

data class RefreshTokenResponse(
    val accessToken: String
)

data class MessageResponse(
    val message: String
)

// ── Shared DTOs ─────────────────────────────────────────────────────────────

data class AddressDto(
    val country: String? = null,
    val city: String? = null,
    val fullAddress: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val userName: String?,
    val profileImage: String?,
    val role: String,
    val allergies: List<String>,
    val dietPreference: String,
    val activeFridgeId: String?,
    val address: AddressDto?
)

data class UpdateProfileRequest(
    val displayName: String? = null,
    val userName: String? = null,
    val profileImage: String? = null,
    val address: AddressDto? = null,
    val allergies: List<String>? = null,
    val dietPreference: String? = null
)
