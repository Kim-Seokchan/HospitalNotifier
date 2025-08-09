package com.example.hospitalnotifier.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Simple persistent [CookieJar] implementation backed by
 * [SharedPreferences]. Only the session cookie is stored since the server we
 * communicate with uses a single session identifier.
 */
class SharedPrefsCookieJar(context: Context) : CookieJar {
    companion object {
        private const val PREF_NAME = "cookie_store"
        private const val COOKIE_KEY = "session"
        private const val TAG = "SharedPrefsCookieJar"
        private const val DELIMITER = "##"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val cookieString = cookies.joinToString(DELIMITER) { it.toString() }
        val previous = prefs.getString(COOKIE_KEY, null)
        prefs.edit().putString(COOKIE_KEY, cookieString).apply()
        if (previous != cookieString) {
            Log.d(TAG, "Cookie refreshed: $cookieString")
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val stored = prefs.getString(COOKIE_KEY, null) ?: return emptyList()
        return stored.split(DELIMITER).mapNotNull { Cookie.parse(url, it) }
    }

    fun clear() {
        prefs.edit().remove(COOKIE_KEY).apply()
    }
}
