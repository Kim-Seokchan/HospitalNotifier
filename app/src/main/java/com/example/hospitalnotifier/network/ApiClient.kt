package com.example.hospitalnotifier.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "https://www.snuh.org"

    fun create(sessionManager: SessionManager): ApiService {
        val client = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    sessionManager.persistCookies(cookies)
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val stored = sessionManager.getCookie() ?: return emptyList()
                    return stored.split("; ").mapNotNull { Cookie.parse(url, it) }
                }
            })
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(ApiService::class.java).also { sessionManager.apiService = it }
    }
}
