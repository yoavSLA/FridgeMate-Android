package com.project.fridgemate.data.remote

import com.google.gson.Gson
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.data.remote.dto.RefreshTokenRequest
import com.project.fridgemate.data.remote.dto.RefreshTokenResponse
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(private val tokenManager: TokenManager) : Authenticator {

    private val gson = Gson()
    private val httpClient = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_RETRIES) {
            tokenManager.clearTokens()
            return null
        }

        val currentRefreshToken = tokenManager.refreshToken ?: run {
            tokenManager.clearTokens()
            return null
        }

        val newAccessToken = refreshAccessToken(currentRefreshToken) ?: run {
            tokenManager.clearTokens()
            return null
        }

        tokenManager.accessToken = newAccessToken

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .build()
    }

    private fun refreshAccessToken(refreshToken: String): String? {
        return try {
            val body = gson.toJson(RefreshTokenRequest(refreshToken))
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}auth/refresh-token")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { responseBody ->
                    gson.fromJson(responseBody, RefreshTokenResponse::class.java).accessToken
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val MAX_RETRIES = 2
    }
}
