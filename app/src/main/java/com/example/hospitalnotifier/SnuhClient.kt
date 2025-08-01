package com.example.hospitalnotifier

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

object SnuhClient {

    private const val BASE_URL = "https://www.snuh.org"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    // Interceptor to add headers and cookies from SharedPreferences
    private fun createHeaderInterceptor(context: Context): Interceptor {
        return Interceptor { chain ->
            val sharedPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val cookie = sharedPref.getString("sessionCookie", "") ?: ""

            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .header("Referer", "https://www.snuh.org/reservation/reservation.do")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            chain.proceed(request)
        }
    }

    private fun getOkHttpClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(createHeaderInterceptor(context))
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private fun getRetrofit(context: Context): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(getOkHttpClient(context))
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    fun getApi(context: Context): SnuhApi {
        return getRetrofit(context).create(SnuhApi::class.java)
    }
}