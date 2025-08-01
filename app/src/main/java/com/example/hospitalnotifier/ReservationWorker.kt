package com.example.hospitalnotifier

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay

class ReservationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ReservationWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "백그라운드 작업 시작")

        val sharedPref = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val id = sharedPref.getString("id", null)
        val password = sharedPref.getString("password", null)
        val targetMonths = sharedPref.getString("targetMonths", null)
        val telegramToken = sharedPref.getString("telegramToken", null)
        val telegramChatId = sharedPref.getString("telegramChatId", null)

        if (id.isNullOrBlank() || password.isNullOrBlank() || targetMonths.isNullOrBlank()) {
            Log.e(TAG, "사용자 정보가 설정되지 않았습니다.")
            return Result.failure()
        }

        Log.d(TAG, "ID: $id")
        Log.d(TAG, "Target Months: $targetMonths")

        try {
            // 1. (중요) 로그인 페이지에 먼저 접속하여 초기 쿠키를 받음
            val pageResponse = SnuhClient.api.getLoginPage()
            if (!pageResponse.isSuccessful) {
                Log.e(TAG, "로그인 페이지 접근 실패: ${pageResponse.code()}")
                val outputData = workDataOf("error" to "서버에 연결할 수 없습니다.")
                return Result.failure(outputData)
            }

            // 2. 실제 로그인 요청 (CookieJar가 쿠키를 자동으로 첨부)
            val loginResponse = SnuhClient.api.login(userId = id, userPw = password)
            if (!loginResponse.isSuccessful || loginResponse.body()?.contains("입력하신 정보와 일치하는 정보가 없습니다") == true) {
                Log.e(TAG, "로그인 실패: ${loginResponse.code()}")
                val outputData = workDataOf("error" to "로그인 실패: 아이디 또는 비밀번호를 확인해주세요.")
                return Result.failure(outputData)
            }
            Log.d(TAG, "로그인 성공")

            // 3. 예약 조회 요청 (CookieJar가 쿠키를 자동으로 첨부)
            val months = targetMonths.split(",").map { it.trim() }
            val foundDates = mutableListOf<String>()

            for (month in months) {
                val yearMonth = month.split("-")
                if (yearMonth.size != 2) continue

                val year = yearMonth[0]
                val monthStr = yearMonth[1].padStart(2, '0')
                val nextDt = "$year$monthStr" + "01"

                // 더 이상 수동으로 쿠키를 전달할 필요 없음
                val response = SnuhClient.api.checkReservation(
                    deptCd = "OSHS", // 예시 값
                    drCd = "05081",   // 예시 값
                    nextDt = nextDt
                )

                if (response.isSuccessful && response.body() != null) {
                    val responseBody = response.body()!!
                    Log.d(TAG, "$month 예약 조회 결과: $responseBody")
                    // 결과 파싱 (예시: "scheduleList" 포함 여부로 판단)
                    if (responseBody.contains("scheduleList")) { // TODO: 더 정교한 파싱 필요
                        val foundMessage = "[$month] 예약 가능한 날짜 발견!"
                        Log.d(TAG, foundMessage)
                        foundDates.add(month)
                    }
                } else {
                    Log.e(TAG, "$month 예약 조회 실패: ${response.code()}")
                }
                delay(1000) // 각 월 조회 사이 1초 딜레이
            }

            // 4. 텔레그램 알림 전송
            if (foundDates.isNotEmpty()) {
                val message = "🎉 예약 가능한 날짜를 찾았습니다! 🎉\n\n" +
                        foundDates.joinToString("\n") { "- $it" } +
                        "\n\n[지금 바로 예약하기](https://www.snuh.org/reservation/reservation.do)"
                Log.d(TAG, "텔레그램 메시지 발송 시도...")

                if (!telegramToken.isNullOrBlank() && !telegramChatId.isNullOrBlank()) {
                    try {
                        val telegramResponse = TelegramClient.api.sendMessage(
                            token = telegramToken,
                            chatId = telegramChatId,
                            text = message
                        )
                        if (telegramResponse.isSuccessful) {
                            Log.d(TAG, "텔레그램 메시지 발송 성공")
                        } else {
                            Log.e(TAG, "텔레그램 메시지 발송 실패: ${telegramResponse.code()} ${telegramResponse.errorBody()?.string()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "텔레그램 발송 중 오류 발생", e)
                    }
                } else {
                    Log.d(TAG, "텔레그램 토큰 또는 챗 ID가 설정되지 않아 메시지를 발송하지 않습니다.")
                }
                Log.d(TAG, "최종: 예약 가능한 날짜를 찾았습니다.")
            } else {
                Log.d(TAG, "최종: 예약 가능한 날짜가 없습니다.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "작업 중 오류 발생", e)
            val outputData = workDataOf("error" to "작업 중 오류가 발생했습니다: ${e.message}")
            return Result.failure(outputData) // 오류 발생 시 재시도 대신 실패 처리
        }

        Log.d(TAG, "백그라운드 작업 완료")
        return Result.success()
    }
}