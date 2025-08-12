package com.example.hospitalnotifier

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.hospitalnotifier.network.ApiClient
import com.example.hospitalnotifier.network.ScheduleResponse
import com.example.hospitalnotifier.network.TelegramClient
import kotlinx.coroutines.delay
import retrofit2.HttpException
import retrofit2.Response

class ReservationWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started")
        setProgress(workDataOf("status" to "Worker started"))

        val sharedPref = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val id = sharedPref.getString("id", null) ?: run {
            val message = "ID 없음"
            setProgress(workDataOf("status" to message))
            return Result.failure(workDataOf("status" to message))
        }
        val password = sharedPref.getString("password", null) ?: run {
            val message = "비밀번호 없음"
            setProgress(workDataOf("status" to message))
            return Result.failure(workDataOf("status" to message))
        }
        val targetMonths = sharedPref.getString("targetMonths", null) ?: run {
            val message = "조회 월 없음"
            setProgress(workDataOf("status" to message))
            return Result.failure(workDataOf("status" to message))
        }
        val token = sharedPref.getString("telegramToken", null)
        val chatId = sharedPref.getString("telegramChatId", null)

        return try {
            clearCookies()
            val loginResult = startLoginProcess(id, password)
            if (loginResult !is Result.Success) {
                return loginResult
            }

            val api = ApiClient.getSnuhApi()
            val months = targetMonths.split(",").map { it.trim() }
            val availableDates = mutableListOf<String>()
            for (month in months) {
                Log.d(TAG, "Querying month: $month")
                setProgress(workDataOf("status" to "Querying month: $month"))
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
                            Log.w(TAG, "세션 만료 감지 (HTTP ${response.code()})")
                            clearCookies()
                            val reLogin = startLoginProcess(id, password)
                            if (reLogin !is Result.Success) {
                                Log.e(TAG, "세션 재로그인 실패")
                                return reLogin
                            }
                            attempt++
                            continue
                        } else if (!response.isSuccessful) {
                            val code = response.code()
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "예약 조회 실패: HTTP $code, error: $errorBody")
                            val msg = "예약 조회 실패: HTTP $code"
                            setProgress(workDataOf("status" to msg))
                            return if (code in 500..599) Result.retry() else Result.failure(workDataOf("status" to msg))
                        }

                        val requestedYear = parts[0].toInt()
                        val requestedMonth = parts[1].toInt()

                        response.body()?.scheduleList?.forEach { item ->
                            item.meddate?.let { dateString ->
                                // dateString is "YYYYMMDD"
                                if (dateString.length == 8) {
                                    val year = dateString.substring(0, 4).toInt()
                                    val monthOfYear = dateString.substring(4, 6).toInt()
                                    if (year == requestedYear && monthOfYear == requestedMonth) {
                                        availableDates.add(dateString)
                                    }
                                }
                            }
                        }
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "예약 조회 실패", e)
                        setProgress(workDataOf("status" to "예약 조회 실패: ${e.message}"))
                        if (e is HttpException && (e.code() == 401 || e.code() == 302)) {
                            Log.w(TAG, "세션 만료 예외 (HTTP ${e.code()})")
                            clearCookies()
                            val reLogin = startLoginProcess(id, password)
                            if (reLogin !is Result.Success) {
                                Log.e(TAG, "세션 재로그인 실패: ${e.message}")
                                return reLogin
                            }
                            attempt++
                        } else {
                            break
                        }
                    }
                }
                delay(1000)
            }
            var finalMessage = if (availableDates.isEmpty()) {
                "예약 가능한 날짜가 없습니다."
            } else {
                "Available dates: ${availableDates.joinToString()}"
            }

            if (availableDates.isNotEmpty()) {
                if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
                    val telegramWarning = "텔레그램 토큰/Chat ID가 없어 메시지를 보내지 않습니다."
                    Log.w(TAG, telegramWarning)
                    finalMessage += "\n$telegramWarning"
                } else {
                    val distinctDates = availableDates.distinct().sorted()
                    val formattedDates = distinctDates.joinToString("\n") { dateString ->
                        // Format YYYYMMDD to YYYY-MM-DD
                        "- ${dateString.substring(0, 4)}-${dateString.substring(4, 6)}-${dateString.substring(6, 8)}"
                    }
                    val message = """🎉 예약 가능한 날짜를 찾았습니다! 🎉

${formattedDates}

[지금 바로 예약하기](https://www.snuh.org/reservation/reservation.do)"""
                    Log.d(TAG, "텔레그램 메시지 전송 시도...")
                    Log.d(TAG, "Token: ${token.take(5)}... Chat ID: ${chatId}")
                    try {
                        // Retrofit @Path에서 이미 "bot" 접두사를 처리하므로 여기서는 실제 토큰만 전달합니다.
                        val response = TelegramClient.api.sendMessage(token, chatId, message)
                        if (response.isSuccessful) {
                            val telegramSuccessMessage = "텔레그램 메시지를 성공적으로 보냈습니다."
                            Log.d(TAG, telegramSuccessMessage)
                            finalMessage += "\n$telegramSuccessMessage"
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "텔레그램 메시지 전송 실패: ${response.code()}, $errorBody")
                            setProgress(workDataOf("status" to "텔레그램 메시지 전송 실패: ${response.code()}"))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "텔레그램 메시지 전송 중 예외 발생", e)
                        setProgress(workDataOf("status" to "텔레그램 메시지 전송 실패: ${e.message}"))
                    }
                }
            }

            Result.success(workDataOf("status" to finalMessage))
        } catch (_: Exception) {
            Result.retry()
        }
    }

    internal suspend fun startLoginProcess(id: String, password: String): Result {
        return try {
            val loginApi = ApiClient.getLoginApi()
            loginApi.initSession()
            val response = loginApi.login(id, password)
            if (response.contains("login.do")) {
                Log.e(TAG, "로그인 실패 응답: $response")
                val message = "로그인 실패: login.do 응답"
                setProgress(workDataOf("status" to message))
                clearCookies()
                Result.failure(workDataOf("status" to message))
            } else {
                val cookieJar = ApiClient.getOkHttpClient().cookieJar as MyCookieJar
                val cookies = cookieJar.getCookies("https://www.snuh.org/")
                val session = cookies.firstOrNull { it.name.startsWith("JSESSIONID") }

                if (session == null) {
                    Log.e(TAG, "세션 쿠키(JSESSIONID) 미확보")
                    val message = "세션 쿠키 없음"
                    setProgress(workDataOf("status" to message))
                    clearCookies()
                    Result.failure(workDataOf("status" to message))
                } else {
                    Log.d(TAG, "세션 쿠키 확보: ${session.name}=${session.value}")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "로그인 실패: ${e.message}")
            setProgress(workDataOf("status" to "로그인 실패: ${e.message}"))
            clearCookies()
            Result.retry()
        }
    }

    private fun clearCookies() {
        val cookieJar = ApiClient.getOkHttpClient().cookieJar as MyCookieJar
        cookieJar.clear()
    }

    private fun clearLoginInfo() {
        appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .remove("id")
            .remove("password")
            .apply()
    }

    companion object {
        private const val TAG = "ReservationWorker"
    }
}
