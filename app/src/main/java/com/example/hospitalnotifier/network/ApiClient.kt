package com.example.hospitalnotifier.network

import android.content.Context
import com.example.hospitalnotifier.MyCookieJar
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

object ApiClient {
    private const val BASE_URL = "https://www.snuh.org/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"

    private fun baseClient(context: Context): OkHttpClient.Builder {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // HEADERS level ensures we can verify Set-Cookie headers for session handling
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .cookieJar(MyCookieJar(context))
            .addInterceptor(loggingInterceptor)
    }

    private fun gson() = GsonBuilder().setLenient().create()

    fun getSnuhApi(context: Context): SnuhApi {
        val okHttpClient = baseClient(context)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Referer", "https://www.snuh.org/reservation/reservation.do")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Requested-With", "XMLHttpRequest")
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson()))
            .build()

        return retrofit.create(SnuhApi::class.java)
    }

    fun getLoginApi(context: Context): SnuhLoginApi {
        val okHttpClient = baseClient(context)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Referer", "https://www.snuh.org/login/login.do")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Requested-With", "XMLHttpRequest")
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson()))
            .build()

        return retrofit.create(SnuhLoginApi::class.java)
    }
}
