package com.example.hospitalnotifier.network

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Builds [ApiService] instances backed by an [OkHttpClient] that persists
 * cookies into [android.content.SharedPreferences].
 */
object ApiClient {
    private const val BASE_URL = "https://www.snuh.org"

    /**
     * Create a new [ApiService] instance. A fresh [OkHttpClient] with a
     * [SharedPrefsCookieJar] is created for each call to ensure that tests can
     * operate in isolation.
     */
    fun create(context: Context, baseUrl: String = BASE_URL): ApiService {
        val cookieJar = SharedPrefsCookieJar(context)
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
