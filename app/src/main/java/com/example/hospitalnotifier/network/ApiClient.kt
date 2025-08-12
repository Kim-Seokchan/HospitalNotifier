package com.example.hospitalnotifier.network

import android.content.Context
import com.example.hospitalnotifier.MyCookieJar
import com.google.gson.GsonBuilder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

object ApiClient {
    private const val BASE_URL = "https://www.snuh.org/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var snuhApi: SnuhApi
    private lateinit var loginApi: SnuhLoginApi

    fun init(context: Context) {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        okHttpClient = OkHttpClient.Builder()
            .cookieJar(MyCookieJar(context))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .build()

        snuhApi = retrofit.create(SnuhApi::class.java)
        loginApi = retrofit.create(SnuhLoginApi::class.java)
    }

    fun getSnuhApi(): SnuhApi {
        if (!::snuhApi.isInitialized) {
            throw IllegalStateException("ApiClient must be initialized")
        }
        return snuhApi
    }

    fun getLoginApi(): SnuhLoginApi {
        if (!::loginApi.isInitialized) {
            throw IllegalStateException("ApiClient must be initialized")
        }
        return loginApi
    }

    fun getOkHttpClient(): OkHttpClient {
        if (!::okHttpClient.isInitialized) {
            throw IllegalStateException("ApiClient must be initialized")
        }
        return okHttpClient
    }
}
