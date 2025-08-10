package com.example.hospitalnotifier

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.hospitalnotifier.network.ApiClient
import com.example.hospitalnotifier.network.ScheduleResponse
import com.example.hospitalnotifier.network.TelegramClient
import kotlinx.coroutines.delay
import retrofit2.HttpException
import retrofit2.Response

class ReservationWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sharedPref = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val id = sharedPref.getString("id", null) ?: return Result.failure()
        val password = sharedPref.getString("password", null) ?: return Result.failure()
        val targetMonths = sharedPref.getString("targetMonths", null) ?: return Result.failure()
        val token = sharedPref.getString("telegramToken", null)
        val chatId = sharedPref.getString("telegramChatId", null)

        return try {
            if (!startLoginProcess(id, password)) {
                return Result.retry()
            }

            val api = ApiClient.getSnuhApi(appContext)
            val months = targetMonths.split(",").map { it.trim() }
            val availableDates = mutableListOf<String>()
            for (month in months) {
                val parts = month.split("-")
                if (parts.size != 2) continue
                val nextDt = parts[0] + parts[1].padStart(2, '0') + "01"
                var attempt = 0
                while (attempt < 2) {
                    try {
                        val response: Response<ScheduleResponse> = api.checkAvailability(
                            hspCd = "1",
                            deptCd = "OSHS",
                            drCd = "05081",
                            nextDt = nextDt
                        )

                        if (response.code() == 401 || response.code() == 302) {
                            Log.w(TAG, "세션 만료 감지 (HTTP ${'$'}{response.code()})")
                            if (!startLoginProcess(id, password)) {
                                Log.e(TAG, "세션 재로그인 실패")
                                return Result.retry()
                            }
                            attempt++
                            continue
                        }

                        response.body()?.scheduleList?.forEach { item ->
                            item.meddate?.let { availableDates.add(it) }
                        }
                        break
                    } catch (e: Exception) {
                        if (e is HttpException && (e.code() == 401 || e.code() == 302)) {
                            Log.w(TAG, "세션 만료 예외 (HTTP ${'$'}{e.code()})")
                            if (!startLoginProcess(id, password)) {
                                Log.e(TAG, "세션 재로그인 실패: ${'$'}{e.message}")
                                return Result.retry()
                            }
                            attempt++
                        } else {
                            break
                        }
                    }
                }
                delay(1000)
            }
            if (availableDates.isNotEmpty() && !token.isNullOrBlank() && !chatId.isNullOrBlank()) {
                val distinctDates = availableDates.distinct().sorted()
                val message = """🎉 예약 가능한 날짜를 찾았습니다! 🎉\n\n${distinctDates.joinToString("\n") { "- $it" }}\n\n[지금 바로 예약하기](https://www.snuh.org/reservation/reservation.do)"""
                try {
                    TelegramClient.api.sendMessage("bot$token", chatId, message)
                } catch (_: Exception) {
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun startLoginProcess(id: String, password: String): Boolean {
        return try {
            val response = ApiClient.getLoginApi(appContext).login(id, password)
            if (response.contains("SUCCESS")) {
                true
            } else {
                Log.e(TAG, "로그인 실패 응답: $response")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "로그인 실패: ${'$'}{e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "ReservationWorker"
    }
}
