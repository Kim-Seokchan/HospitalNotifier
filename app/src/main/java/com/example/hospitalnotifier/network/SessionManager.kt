package com.example.hospitalnotifier.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)

    lateinit var apiService: ApiService

    fun saveCredentials(id: String, pw: String) {
        prefs.edit().putString("userId", id).putString("userPw", pw).apply()
    }

    fun persistCookies(cookies: List<Cookie>) {
        if (cookies.isNotEmpty()) {
            val cookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
            val expiresAt = cookies.maxOf { it.expiresAt }
            prefs.edit().putString("cookie", cookieHeader)
                .putLong("expires", expiresAt)
                .apply()
        }
    }

    fun getCookie(): String? {
        val cookie = prefs.getString("cookie", null)
        val expires = prefs.getLong("expires", 0L)
        return if (cookie != null && System.currentTimeMillis() < expires) cookie else null
    }

    private fun getCredentials(): Pair<String, String>? {
        val id = prefs.getString("userId", null)
        val pw = prefs.getString("userPw", null)
        return if (id != null && pw != null) Pair(id, pw) else null
    }

    suspend fun ensureSession(): String {
        val existing = getCookie()
        if (existing != null) return existing
        val creds = getCredentials() ?: throw IllegalStateException("No credentials")
        return withContext(Dispatchers.IO) {
            val response = apiService.login(creds.first, creds.second)
            if (!response.isSuccessful) throw Exception("login failed")
            getCookie() ?: ""
        }
    }
}
