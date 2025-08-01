package com.example.hospitalnotifier.network

import android.content.Context
import com.example.hospitalnotifier.MyCookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "https://www.snuh.org/"

    fun getApiService(context: Context): SnuhApi {
        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(MyCookieJar(context))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(SnuhApi::class.java)
    }
}