package com.project.fridgemate.data.remote

import android.content.Context
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

object PicassoCache {

    private const val DISK_CACHE_SIZE = 50L * 1024 * 1024 // 50 MB

    fun init(context: Context) {
        val cacheDir = File(context.cacheDir, "image_cache")
        val cache = Cache(cacheDir, DISK_CACHE_SIZE)

        val okHttpClient = OkHttpClient.Builder()
            .cache(cache)
            .build()

        val picasso = Picasso.Builder(context)
            .downloader(OkHttp3Downloader(okHttpClient))
            .build()

        // picasso.setIndicatorsEnabled(true) // cache indicators
        Picasso.setSingletonInstance(picasso)
    }
}
