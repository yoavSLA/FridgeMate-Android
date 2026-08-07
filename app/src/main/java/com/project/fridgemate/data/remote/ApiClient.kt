package com.project.fridgemate.data.remote

import android.content.Context
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.data.remote.api.AuthApi
import com.project.fridgemate.data.remote.api.ScanApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TIMEOUT_SECONDS = 30L

    // A scan blocks on an AI vision call, which regularly outlives the shared timeout.
    private const val SCAN_TIMEOUT_SECONDS = 120L

    private lateinit var tokenManager: TokenManager
    private lateinit var publicRetrofit: Retrofit
    private lateinit var authenticatedRetrofit: Retrofit
    private lateinit var scanRetrofit: Retrofit

    fun init(context: Context) {
        tokenManager = TokenManager(context.applicationContext)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val publicClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator(tokenManager))
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val scanClient = authenticatedClient.newBuilder()
            .readTimeout(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        publicRetrofit = buildRetrofit(publicClient)
        authenticatedRetrofit = buildRetrofit(authenticatedClient)
        scanRetrofit = buildRetrofit(scanClient)
    }

    fun getTokenManager(): TokenManager = tokenManager

    fun getAuthApi(): AuthApi = publicRetrofit.create(AuthApi::class.java)

    fun getJournalApi(): com.project.fridgemate.data.remote.api.JournalApi = authenticatedRetrofit.create(com.project.fridgemate.data.remote.api.JournalApi::class.java)

    fun getScanApi(): ScanApi = scanRetrofit.create(ScanApi::class.java)

    fun <T> createApi(apiClass: Class<T>): T = authenticatedRetrofit.create(apiClass)

    private fun buildRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
