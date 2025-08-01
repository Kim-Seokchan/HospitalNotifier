package com.example.hospitalnotifier

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.hospitalnotifier.network.ApiClient
import kotlinx.coroutines.delay

class ReservationWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ReservationWorker"
    }

    override suspend fun doWork(): Result {
        val log = mutableListOf<String>()
        log.add("백그라운드 작업 시작")

        val sharedPref = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val sessionCookie = sharedPref.getString("sessionCookie", null)
        val targetMonths = sharedPref.getString("targetMonths", null)
        val telegramToken = sharedPref.getString("telegramToken", null)
        val telegramChatId = sharedPref.getString("telegramChatId", null)

        if (sessionCookie.isNullOrBlank()) {
            log.add("세션 쿠키가 없습니다. 로그인이 필요합니다.")
            return Result.failure(workDataOf("error" to "로그인이 필요합니다.", "log" to log.joinToString("\n")))
        }

        if (targetMonths.isNullOrBlank()) {
            log.add("조회할 월 정보가 없습니다.")
            return Result.failure(workDataOf("error" to "조회할 월을 입력해주세요.", "log" to log.joinToString("\n")))
        }

        log.add("저장된 쿠키로 예약 조회를 시작합니다.")

        try {
            val months = targetMonths.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val allFoundDates = mutableListOf<String>()
            val snuhApi = ApiClient.getApiService(appContext)

            for (month in months) {
                val yearMonth = month.split("-")
                if (yearMonth.size != 2) continue

                val year = yearMonth[0]
                val monthStr = yearMonth[1].padStart(2, '0')
                val nextDt = "$year$monthStr" + "01"

                log.add("$year-$monthStr 월 예약 조회 중...")

                val response = snuhApi.checkAvailability(
                    sessionCookie = sessionCookie,
                    deptCd = "OSHS", // TODO: Make this configurable
                    drCd = "05081",   // TODO: Make this configurable
                    nextDt = nextDt
                )

                if (response.isSuccessful && response.body() != null) {
                    val scheduleResponse = response.body()!!
                    if (!scheduleResponse.scheduleList.isNullOrEmpty()) {
                        for (schedule in scheduleResponse.scheduleList) {
                            val meddate = schedule.meddate ?: continue
                            val formattedDate = "${meddate.substring(0, 4)}년 ${meddate.substring(4, 6)}월 ${meddate.substring(6, 8)}일"
                            allFoundDates.add(formattedDate)
                        }
                        log.add("[$month] 예약 가능한 날짜 발견!")
                    } else {
                        log.add("[$month] 예약 가능한 날짜 없음.")
                    }
                } else if (response.code() == 401 || response.code() == 403) { // Unauthorized or Forbidden
                    log.add("세션이 만료되었습니다. 재로그인이 필요합니다.")
                    return Result.failure(workDataOf("error" to "세션 만료. 재로그인 해주세요.", "log" to log.joinToString("\n")))
                } else {
                    log.add("[$month] 예약 조회 실패: ${response.code()} - ${response.errorBody()?.string()}")
                }

                delay(1000) // Rate limit
            }

            if (allFoundDates.isNotEmpty()) {
                val distinctDates = allFoundDates.distinct().sorted()
                val message = "🎉 예약 가능한 날짜를 찾았습니다! 🎉\n\n" +
                        distinctDates.joinToString("\n") { "- $it" } +
                        "\n\n[지금 바로 예약하기](https://www.snuh.org/reservation/reservation.do)"
                log.add("텔레그램 메시지 발송 시도...")
                sendTelegramMessage(telegramToken, telegramChatId, message, log)
            } else {
                log.add("조회한 모든 월에 예약 가능한 날짜가 없습니다.")
            }

            return Result.success(workDataOf("log" to log.joinToString("\n")))

        } catch (e: Exception) {
            log.add("작업 중 오류 발생: ${e.message}")
            Log.e(TAG, "작업 중 오류 발생", e)
            return Result.failure(workDataOf("error" to "작업 중 오류가 발생했습니다.", "log" to log.joinToString("\n")))
        }
    }

    private suspend fun sendTelegramMessage(token: String?, chatId: String?, text: String, log: MutableList<String>) {
        if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
            log.add("텔레그램 토큰 또는 챗 ID가 없어 메시지를 발송하지 않습니다.")
            return
        }
        try {
            val response = TelegramClient.api.sendMessage(
                token = "bot$token",
                chatId = chatId,
                text = text,
                parseMode = "Markdown"
            )
            if (response.isSuccessful) {
                log.add("텔레그램 메시지 발송 성공")
            } else {
                log.add("텔레그램 메시지 발송 실패: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            log.add("텔레그램 발송 중 오류 발생: ${e.message}")
            Log.e(TAG, "텔레그램 발송 중 오류 발생", e)
        }
    }
}
