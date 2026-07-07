package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object SyncClient {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private var currentUrl: String? = null
    private var cachedApi: SyncApi? = null

    /**
     * Retrieves or builds the SyncApi instance with the given baseUrl.
     * baseUrl should be in format "http://ip:port/"
     */
    fun getApiService(baseUrl: String): SyncApi {
        // Sanitize base URL
        val sanitizedUrl = when {
            baseUrl.isBlank() -> "http://localhost/"
            !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://") -> "http://$baseUrl"
            else -> baseUrl
        }.let {
            if (!it.endsWith("/")) "$it/" else it
        }

        if (currentUrl == sanitizedUrl && cachedApi != null) {
            return cachedApi!!
        }

        currentUrl = sanitizedUrl
        val retrofit = Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val api = retrofit.create(SyncApi::class.java)
        cachedApi = api
        return api
    }
}
