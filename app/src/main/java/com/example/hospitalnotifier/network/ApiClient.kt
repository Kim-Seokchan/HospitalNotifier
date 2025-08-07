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

    fun getApiService(context: Context): SnuhApi {
        // 로깅 인터셉터 추가
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // 요청/응답의 본문까지 모두 로그로 출력
        }

        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(MyCookieJar(context))
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Referer", "https://www.snuh.org/reservation/reservation.do")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                    .header("X-Requested-With", "XMLHttpRequest")
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor) // 인터셉터 추가
            .build()

        val gson = GsonBuilder()
            .setLenient()
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create()) // 스칼라 컨버터 추가
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(SnuhApi::class.java)
    }
}