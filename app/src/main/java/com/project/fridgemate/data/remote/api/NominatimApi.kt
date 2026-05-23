package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.NominatimResult
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String,
        @Query("limit") limit: Int,
        @Query("addressdetails") addressdetails: Int
    ): List<NominatimResult>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String,
        @Query("addressdetails") addressdetails: Int
    ): NominatimResult

    companion object {
        val instance: NominatimApi by lazy {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "FridgeMate-Android/1.0")
                        .header("Accept-Language", "en")
                        .build()
                    chain.proceed(request)
                }
                .build()
            Retrofit.Builder()
                .baseUrl("https://nominatim.openstreetmap.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NominatimApi::class.java)
        }
    }
}
