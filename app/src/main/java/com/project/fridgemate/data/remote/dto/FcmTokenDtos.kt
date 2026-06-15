package com.project.fridgemate.data.remote.dto
data class FcmTokenRequest(
    val token: String
)

data class FcmTokenResponse(
    val success: Boolean,
    val message: String? = null
)
