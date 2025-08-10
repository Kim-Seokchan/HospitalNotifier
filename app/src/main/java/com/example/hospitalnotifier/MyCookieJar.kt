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
            val key = "${'$'}{cookie.domain}|${'$'}{cookie.path}|${'$'}{cookie.name}"
            if (cookie.expiresAt < System.currentTimeMillis()) {
                editor.remove(key)
            } else {
                editor.putString(key, cookie.toString())
            }
        }
        editor.apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = mutableListOf<Cookie>()
        for ((_, value) in sharedPreferences.all) {
            if (value is String) {
                val parsed = Cookie.parse(url, value)
                if (parsed != null && parsed.matches(url)) {
                    cookies.add(parsed)
                }
            }
        }
        return cookies
    }
}
