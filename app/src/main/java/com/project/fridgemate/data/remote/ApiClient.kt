package com.project.fridgemate.data.remote

import android.content.Context
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.data.remote.api.AuthApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object ApiClient {

    private const val TIMEOUT_SECONDS = 30L

    private lateinit var tokenManager: TokenManager
    private lateinit var publicRetrofit: Retrofit
    private lateinit var authenticatedRetrofit: Retrofit

    private fun OkHttpClient.Builder.trustAllCertificates(): OkHttpClient.Builder {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        sslSocketFactory(sslContext.socketFactory, trustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }

    fun init(context: Context) {
        tokenManager = TokenManager(context.applicationContext)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val publicClient = OkHttpClient.Builder()
            .trustAllCertificates()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val authenticatedClient = OkHttpClient.Builder()
            .trustAllCertificates()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator(tokenManager))
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        publicRetrofit = buildRetrofit(publicClient)
        authenticatedRetrofit = buildRetrofit(authenticatedClient)
    }

    fun getTokenManager(): TokenManager = tokenManager

    fun getAuthApi(): AuthApi = publicRetrofit.create(AuthApi::class.java)

    fun getJournalApi(): com.project.fridgemate.data.remote.api.JournalApi = authenticatedRetrofit.create(com.project.fridgemate.data.remote.api.JournalApi::class.java)

    fun <T> createApi(apiClass: Class<T>): T = authenticatedRetrofit.create(apiClass)

    private fun buildRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
