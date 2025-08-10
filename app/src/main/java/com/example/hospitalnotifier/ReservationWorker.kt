package com.example.hospitalnotifier

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.hospitalnotifier.network.ApiClient
import com.example.hospitalnotifier.network.ScheduleResponse
import com.example.hospitalnotifier.network.TelegramClient
import kotlinx.coroutines.delay

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
            // 로그인 시도 (쿠키 저장)
            ApiClient.getLoginApi(appContext).login(id, password)

            val api = ApiClient.getSnuhApi(appContext)
            val months = targetMonths.split(",").map { it.trim() }
            val availableDates = mutableListOf<String>()
            for (month in months) {
                val parts = month.split("-")
                if (parts.size != 2) continue
                val nextDt = parts[0] + parts[1].padStart(2, '0') + "01"
                try {
                    val response: ScheduleResponse = api.checkAvailability(
                        hspCd = "1",
                        deptCd = "OSHS",
                        drCd = "05081",
                        nextDt = nextDt
                    )
                    response.scheduleList?.forEach { item ->
                        item.meddate?.let { availableDates.add(it) }
                    }
                } catch (_: Exception) {
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
}
