package com.project.fridgemate.data.remote

import android.content.Context
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.data.remote.api.AuthApi
import io.socket.client.IO
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TIMEOUT_SECONDS = 30L

    // Temporary DNS overrides until public A-records exist. TLS still validates
    // against the requested hostname, so cert coverage of *.cs.colman.ac.il works.
    // Remove entries here once the academy adds the public DNS.
    private val hostOverrides = mapOf(
        "fridgemate.cs.colman.ac.il" to "193.106.55.84"
    )

    val dns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return hostOverrides[hostname]?.let { ip -> listOf(InetAddress.getByName(ip)) }
                ?: Dns.SYSTEM.lookup(hostname)
        }
    }

    // Bare OkHttp used as default for socket.io (needs same Dns; no interceptors).
    private val socketHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(dns)
        .build()

    private lateinit var tokenManager: TokenManager
    private lateinit var publicRetrofit: Retrofit
    private lateinit var authenticatedRetrofit: Retrofit

    fun init(context: Context) {
        tokenManager = TokenManager(context.applicationContext)

        // Make socket.io honour the same DNS overrides as Retrofit.
        IO.setDefaultOkHttpCallFactory(socketHttpClient)
        IO.setDefaultOkHttpWebSocketFactory(socketHttpClient)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val publicClient = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val authenticatedClient = OkHttpClient.Builder()
            .dns(dns)
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
