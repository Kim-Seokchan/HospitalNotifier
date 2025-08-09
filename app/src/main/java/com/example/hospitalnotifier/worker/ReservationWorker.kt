package com.example.hospitalnotifier.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.hospitalnotifier.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException

class ReservationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // MainActivity에서 전달한 데이터 받기
        val userId = inputData.getString("USER_ID") ?: return Result.failure()
        val userPw = inputData.getString("USER_PW") ?: return Result.failure()
        val token = inputData.getString("TELEGRAM_TOKEN") ?: return Result.failure()
        val chatId = inputData.getString("TELEGRAM_CHAT_ID") ?: return Result.failure()

        Log.d("ReservationWorker", "작업 시작: $userId")

        try {
            val api = ApiClient.create(applicationContext)

            // 1. 로그인 시도하여 세션 쿠키를 확보
            if (!ensureLoggedIn(api, userId, userPw)) {
                Log.e("ReservationWorker", "로그인 실패")
                return Result.retry()
            }

            // 2. 예약 확인 요청 (Python 코드의 check_reservation 로직)
            val monthsToCheck = listOf("20250701", "20250801")
            var foundDates = ""

            for (month in monthsToCheck) {
                try {
                    val response = api.checkAvailability("OSHS", "05081", month)
                    response.scheduleList?.forEach {
                        it.meddate?.let { date ->
                            foundDates += "$date\n"
                        }
                    }
                } catch (e: HttpException) {
                    // 세션 만료 가능성. 재로그인 후 한 번 더 시도
                    if (e.code() == 401 || (e.code() in 300..399)) {
                        Log.d("ReservationWorker", "세션 만료로 쿠키 갱신 시도")
        
                        if (ensureLoggedIn(api, userId, userPw)) {
                            val retry = api.checkAvailability("OSHS", "05081", month)
                            retry.scheduleList?.forEach {
                                it.meddate?.let { date ->
                                    foundDates += "$date\n"
                                }
                            }
                        } else {
                            return Result.retry()
                        }
                    } else {
                        throw e
                    }
                }
            }

            // 3. 예약 가능한 날짜가 있으면 텔레그램 메시지 발송
            if (foundDates.isNotEmpty()) {
                val message = "🎉 예약 가능한 날짜를 찾았습니다!\n$foundDates"
                sendTelegramMessage(message, token, chatId)
                Log.d("ReservationWorker", "예약 가능 날짜 발견 및 알림 발송!")
            } else {
                Log.d("ReservationWorker", "빈자리 없음.")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("ReservationWorker", "오류 발생: ${e.message}")
            return Result.retry()
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

    private suspend fun ensureLoggedIn(api: com.example.hospitalnotifier.network.ApiService, id: String, pw: String): Boolean {
        return try {
            api.login(id, pw).isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}