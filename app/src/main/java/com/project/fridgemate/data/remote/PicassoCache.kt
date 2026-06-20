package com.project.fridgemate.data.remote

import android.content.Context
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object PicassoCache {

    private const val DISK_CACHE_SIZE = 50L * 1024 * 1024 // 50 MB

    fun init(context: Context) {
        val cacheDir = File(context.cacheDir, "image_cache")
        val cache = Cache(cacheDir, DISK_CACHE_SIZE)

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .cache(cache)
            .build()

        val picasso = Picasso.Builder(context)
            .downloader(OkHttp3Downloader(okHttpClient))
            .build()

        // picasso.setIndicatorsEnabled(true) // cache indicators
        Picasso.setSingletonInstance(picasso)
    }
}
