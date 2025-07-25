package com.example.hospitalnotifier.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.hospitalnotifier.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ReservationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString("USER_ID") ?: return Result.failure()
        val userPw = inputData.getString("USER_PW") ?: return Result.failure()
        val isOneTimeCheck = inputData.getBoolean("ONE_TIME_CHECK", false)

        Log.d("ReservationWorker", "작업 시작: $userId, 일회성 확인: $isOneTimeCheck")

        return try {
            val resultMessage = checkReservation(userId, userPw)

            if (isOneTimeCheck) {
                // 일회성 확인인 경우, 결과를 MainActivity로 전달
                val outputData = workDataOf("RESULT" to resultMessage)
                Result.success(outputData)
            } else {
                // 주기적 확인인 경우, 빈 자리가 있을 때만 텔레그램 전송
                if (!resultMessage.contains("빈자리 없음")) {
                    val token = inputData.getString("TELEGRAM_TOKEN") ?: return Result.failure()
                    val chatId = inputData.getString("TELEGRAM_CHAT_ID") ?: return Result.failure()
                    sendTelegramMessage(resultMessage, token, chatId)
                    Log.d("ReservationWorker", "예약 가능 날짜 발견 및 알림 발송!")
                }
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("ReservationWorker", "오류 발생: ${e.message}")
            if (isOneTimeCheck) {
                val outputData = workDataOf("RESULT" to "오류 발생: ${e.message}")
                Result.failure(outputData)
            } else {
                Result.retry()
            }
        }
    }

    // 예약 확인 로직
    private suspend fun checkReservation(userId: String, userPw: String): String {
        // 1. 로그인 시도
        val loginResponse = ApiClient.instance.login(userId, userPw)
        if (!loginResponse.isSuccessful) {
            throw Exception("로그인 실패")
        }

        // 2. 응답 헤더에서 세션 쿠키 가져오기
        val cookies = loginResponse.headers().values("Set-Cookie")
        val sessionCookie = cookies.joinToString(separator = "; ")
        if (sessionCookie.isEmpty()) {
            throw Exception("세션 쿠키 얻기 실패")
        }

        // 3. 예약 확인 요청
        val monthsToCheck = listOf("20250701", "20250801")
        var foundDates = ""

        for (month in monthsToCheck) {
            val response = ApiClient.instance.checkAvailability(sessionCookie, "OSHS", "05081", month)
            response.scheduleList?.forEach {
                if (it.meddate != null) {
                    foundDates += "${it.meddate}\n"
                }
            }
        }

        // 4. 결과 메시지 생성
        return if (foundDates.isNotEmpty()) {
            "🎉 예약 가능한 날짜를 찾았습니다!\n$foundDates"
        } else {
            "아쉽게도 빈자리가 없습니다."
        }
    }

    // 텔레그램 메시지 보내는 함수
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