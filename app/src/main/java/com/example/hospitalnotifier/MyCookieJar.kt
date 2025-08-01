package com.example.hospitalnotifier

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class MyCookieJar(context: Context) : CookieJar {
    private val sharedPreferences = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val editor = sharedPreferences.edit()
        for (cookie in cookies) {
            editor.putString(cookie.name, cookie.value)
        }
        editor.apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = mutableListOf<Cookie>()
        for ((name, value) in sharedPreferences.all) {
            if (value is String) {
                cookies.add(Cookie.Builder().name(name).value(value).domain(url.host).build())
            }
        }
        return cookies
    }
}
