package com.project.fridgemate.data.repository

import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.TokenManager
import com.project.fridgemate.data.remote.api.AuthApi
import com.project.fridgemate.data.remote.dto.ForgotPasswordRequest
import com.project.fridgemate.data.remote.dto.GoogleLoginRequest
import com.project.fridgemate.data.remote.dto.LoginRequest
import com.project.fridgemate.data.remote.dto.RegisterRequest
import com.project.fridgemate.data.remote.dto.ResetPasswordRequest
import com.project.fridgemate.utils.AuthResult

class AuthRepository : BaseRepository() {

    private val authApi: AuthApi = ApiClient.getAuthApi()
    private val tokenManager: TokenManager = ApiClient.getTokenManager()

    val isLoggedIn: Boolean
        get() = tokenManager.isLoggedIn

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            val response = authApi.login(LoginRequest(email.trim(), password))
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenManager.saveTokens(body.accessToken, body.refreshToken)
                AuthResult.Success
            } else {
                AuthResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun loginWithGoogle(idToken: String): AuthResult {
        return try {
            val response = authApi.loginWithGoogle(GoogleLoginRequest(idToken))
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenManager.saveTokens(body.accessToken, body.refreshToken)
                AuthResult.Success
            } else {
                AuthResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun register(displayName: String, email: String, password: String): AuthResult {
        return try {
            val response = authApi.register(RegisterRequest(email.trim(), password, displayName.trim()))
            if (response.isSuccessful) {
                AuthResult.Success
            } else {
                AuthResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun forgotPassword(email: String): AuthResult {
        return try {
            val response = authApi.forgotPassword(ForgotPasswordRequest(email.trim()))
            if (response.isSuccessful) {
                AuthResult.Success
            } else {
                AuthResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResult {
        return try {
            val response = authApi.resetPassword(ResetPasswordRequest(email.trim(), code.trim(), newPassword))
            if (response.isSuccessful) {
                AuthResult.Success
            } else {
                AuthResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun logout(): AuthResult {
        return try {
            val authenticatedApi = ApiClient.createApi(AuthApi::class.java)
            authenticatedApi.logout()
            tokenManager.clearTokens()
            AuthResult.Success
        } catch (e: Exception) {
            tokenManager.clearTokens()
            AuthResult.Success
        }
    }


}
