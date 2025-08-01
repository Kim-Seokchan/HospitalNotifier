package com.example.hospitalnotifier.network

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

object ApiClient {
    // 병원 웹사이트 주소
    private const val BASE_URL = "https://www.snuh.org"

    val instance: ApiService by lazy {
        // 로깅 인터셉터 추가
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // OkHttp 클라이언트 설정
            .addConverterFactory(ScalarsConverterFactory.create()) // 일반 텍스트 변환기 추가
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .build()
        retrofit.create(ApiService::class.java)
    }
}