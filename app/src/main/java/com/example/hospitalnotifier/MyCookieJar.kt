package com.example.hospitalnotifier

import android.content.Context
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class MyCookieJar(context: Context) : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        Log.d("MyCookieJar", "Saving cookies for host: $host")
        val newCookies = cookieStore.getOrPut(host) { mutableListOf() }

        for (cookie in cookies) {
            Log.d("MyCookieJar", "  - Cookie: ${cookie.name}=${cookie.value}; domain=${cookie.domain}; path=${cookie.path}; expiresAt=${cookie.expiresAt}")
            // Remove existing cookie with the same name before adding the new one
            newCookies.removeAll { it.name == cookie.name }
            newCookies.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val validCookies = mutableListOf<Cookie>()
        val hosts = cookieStore.keys

        for (host in hosts) {
            val cookies = cookieStore[host]!!
            val cookiesToRemove = mutableListOf<Cookie>()

            for (cookie in cookies) {
                if (cookie.expiresAt < System.currentTimeMillis()) {
                    cookiesToRemove.add(cookie)
                } else if (cookie.matches(url)) {
                    validCookies.add(cookie)
                }
            }

            cookies.removeAll(cookiesToRemove)
        }

        validCookies.forEach { cookie ->
            Log.d("MyCookieJar", "Loading cookie for ${url.host}: ${cookie.name}=${cookie.value}")
        }

        return validCookies
    }

    fun clear() {
        cookieStore.clear()
    }

    fun getCookies(url: String): List<Cookie> {
        val httpUrl = url.toHttpUrl()
        return loadForRequest(httpUrl)
    }
}
