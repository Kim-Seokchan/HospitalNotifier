package com.example.hospitalnotifier.repository

import android.util.Log
import com.example.hospitalnotifier.network.ApiService
import com.example.hospitalnotifier.network.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ReservationRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun checkAndNotify(months: List<String>, token: String, chatId: String) {
        try {
            sessionManager.ensureSession()
            var foundDates = ""
            for (month in months) {
                val response = api.checkAvailability("OSHS", "05081", month)
                response.scheduleList?.forEach { item ->
                    item.meddate?.let { foundDates += "$it\n" }
                }
            }
            if (foundDates.isNotEmpty()) {
                val message = "🎉 예약 가능한 날짜를 찾았습니다!\n$foundDates"
                sendTelegramMessage(message, token, chatId)
                Log.d("ReservationRepository", "예약 가능 날짜 발견 및 알림 발송!")
            } else {
                Log.d("ReservationRepository", "빈자리 없음.")
            }
        } catch (e: Exception) {
            Log.e("ReservationRepository", "오류: ${e.message}")
            throw e
        }
    }

    private suspend fun sendTelegramMessage(message: String, token: String, chatId: String) {
        val url = "https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=$message"
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("Telegram", "메시지 발송 성공")
                    } else {
                        Log.e("Telegram", "메시지 발송 실패: ${response.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("Telegram", "네트워크 오류: ${e.message}")
            }
        }
    }
}
