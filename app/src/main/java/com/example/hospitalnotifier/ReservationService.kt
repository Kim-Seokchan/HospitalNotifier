package com.example.hospitalnotifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.hospitalnotifier.network.ApiClient
import com.example.hospitalnotifier.network.ScheduleResponse
import com.example.hospitalnotifier.network.TelegramClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Response
import java.util.concurrent.TimeUnit

class ReservationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var intervalMinutes = 15L
    private var currentState = State.IDLE

    private enum class State {
        IDLE,
        LOGGING_IN,
        CHECKING,
        RE_LOGGING_IN,
        WAITING_FOR_RETRY,
        SUCCESS_AND_STOPPED
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intervalMinutes = intent?.getLongExtra("interval", 15L) ?: 15L
        startForeground(NOTIFICATION_ID, createNotification("예약 조회 서비스 시작 중..."))

        log("서비스가 시작되었습니다. 조회 주기: $intervalMinutes 분")
        changeState(State.LOGGING_IN)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // 모든 예약된 작업을 취소
        log("서비스가 중지되었습니다.")
    }

    private fun changeState(newState: State) {
        if (currentState == State.SUCCESS_AND_STOPPED) return
        currentState = newState
        Log.d(TAG, "State changed to: $currentState")
        runNextAction()
    }

    private fun runNextAction() {
        if (currentState == State.SUCCESS_AND_STOPPED) return

        CoroutineScope(Dispatchers.IO).launch {
            when (currentState) {
                State.LOGGING_IN -> performLogin()
                State.CHECKING -> performCheck()
                State.RE_LOGGING_IN -> performReLogin()
                State.WAITING_FOR_RETRY -> waitForRetry()
                else -> {}
            }
        }
    }

    private suspend fun performLogin() {
        log("로그인을 시도합니다...")
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val id = sharedPref.getString("id", null)
        val password = sharedPref.getString("password", null)

        if (id == null || password == null) {
            log("ID 또는 비밀번호가 저장되지 않았습니다. 서비스를 중지합니다.")
            stopSelf()
            return
        }

        clearCookies()
        val success = startLoginProcess(id, password)
        if (success) {
            log("로그인 성공.")
            changeState(State.CHECKING)
        } else {
            log("로그인에 실패했습니다. ID와 비밀번호를 확인해주세요.")
            stopSelf()
        }
    }

    private suspend fun performCheck() {
        log("예약 가능 여부를 확인합니다...")
        updateNotification("예약 가능 여부를 확인 중입니다...")

        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val id = sharedPref.getString("id", null)
        val password = sharedPref.getString("password", null)
        val targetMonths = sharedPref.getString("targetMonths", null)

        if (id == null || password == null || targetMonths == null) {
            log("필수 정보가 없습니다. 서비스를 중지합니다.")
            stopSelf()
            return
        }

        val api = ApiClient.getSnuhApi()
        val months = targetMonths.split(",").map { it.trim() }
        val availableDates = mutableListOf<String>()
        var sessionExpired = false

        for (month in months) {
            log("조회 월: $month")
            val parts = month.split("-")
            if (parts.size != 2) continue
            val nextDt = parts[0] + parts[1].padStart(2, '0') + "01"

            try {
                val response: Response<ScheduleResponse> = api.checkAvailability(
                    hspCd = "1", deptCd = "OSHS", drCd = "05081", nextDt = nextDt
                )

                if (response.code() == 401 || response.code() == 302) {
                    sessionExpired = true
                    break
                }

                if (!response.isSuccessful) {
                    log("예약 조회 실패: HTTP ${response.code()}")
                    continue
                }

                val requestedYear = parts[0].toInt()
                val requestedMonth = parts[1].toInt()
                response.body()?.scheduleList?.forEach { item ->
                    item.meddate?.let { dateString ->
                        if (dateString.length == 8) {
                            val year = dateString.substring(0, 4).toInt()
                            val monthOfYear = dateString.substring(4, 6).toInt()
                            if (year == requestedYear && monthOfYear == requestedMonth) {
                                availableDates.add(dateString)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log("예약 조회 중 오류 발생: ${e.message}")
                sessionExpired = true // 네트워크 오류도 세션 만료로 간주하고 재로그인 시도
                break
            }
        }

        if (sessionExpired) {
            log("세션이 만료되었거나 네트워크 오류가 발생했습니다.")
            changeState(State.RE_LOGGING_IN)
        } else if (availableDates.isNotEmpty()) {
            val formattedDates = availableDates.distinct().joinToString { formatDate(it) }
            val message = "🎉 예약 가능한 날짜를 찾았습니다: $formattedDates"
            log(message)
            updateNotification("예약 가능! 앱에서 확인하세요.")

            val token = sharedPref.getString("telegramToken", null)
            val chatId = sharedPref.getString("telegramChatId", null)
            if (!token.isNullOrBlank() && !chatId.isNullOrBlank()) {
                sendTelegramMessage(token, chatId, message)
            } else {
                log("텔레그램 정보가 없어 메시지를 보내지 않았습니다.")
            }
            changeState(State.SUCCESS_AND_STOPPED)
            stopSelf()
        } else {
            log("예약 가능한 날짜가 없습니다. $intervalMinutes 분 후에 다시 확인합니다.")
            updateNotification("$intervalMinutes 분 후에 다시 확인합니다.")
            handler.postDelayed({ changeState(State.CHECKING) }, TimeUnit.MINUTES.toMillis(intervalMinutes))
        }
    }

    private suspend fun performReLogin() {
        log("세션 갱신을 위해 재로그인을 시도합니다...")
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val id = sharedPref.getString("id", null)
        val password = sharedPref.getString("password", null)

        if (id == null || password == null) {
            log("ID/PW 정보가 없어 재로그인할 수 없습니다. 서비스를 중지합니다.")
            stopSelf()
            return
        }

        clearCookies()
        val success = startLoginProcess(id, password)
        if (success) {
            log("재로그인 성공.")
            changeState(State.CHECKING)
        } else {
            log("재로그인에 실패했습니다.")
            changeState(State.WAITING_FOR_RETRY)
        }
    }

    private fun waitForRetry() {
        log("15분 후 재로그인을 다시 시도합니다.")
        updateNotification("재로그인 실패. 15분 후 다시 시도합니다.")
        handler.postDelayed({ changeState(State.RE_LOGGING_IN) }, TimeUnit.MINUTES.toMillis(15))
    }

    private suspend fun startLoginProcess(id: String, password: String): Boolean {
        return try {
            val loginApi = ApiClient.getLoginApi()
            loginApi.initSession()
            val response = loginApi.login(id, password)
            if (response.contains("login.do")) {
                false
            } else {
                val cookieJar = ApiClient.getOkHttpClient().cookieJar as MyCookieJar
                val cookies = cookieJar.getCookies("https://www.snuh.org/")
                cookies.any { it.name.startsWith("JSESSIONID") }
            }
        } catch (e: Exception) {
            log("로그인 프로세스 중 예외 발생: ${e.message}")
            false
        }
    }

    private suspend fun sendTelegramMessage(token: String, chatId: String, text: String) {
        log("텔레그램 메시지 전송을 시도합니다...")
        try {
            val response = TelegramClient.api.sendMessage(token, chatId, text)
            if (response.isSuccessful) {
                log("텔레그램 메시지를 성공적으로 전송했습니다.")
            } else {
                log("텔레그램 메시지 전송 실패: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            log("텔레그램 메시지 전송 중 예외 발생: ${e.message}")
        }
    }

    private fun clearCookies() {
        (ApiClient.getOkHttpClient().cookieJar as? MyCookieJar)?.clear()
        log("쿠키를 초기화했습니다.")
    }
    
    private fun formatDate(dateString: String): String {
        return if (dateString.length == 8) {
            "${dateString.substring(0, 4)}-${dateString.substring(4, 6)}-${dateString.substring(6, 8)}"
        } else {
            dateString
        }
    }

    private fun createNotification(contentText: String): android.app.Notification {
        val notificationChannelId = "RESERVATION_SERVICE_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Reservation Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("서울대병원 예약 알리미")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        val intent = Intent("log-message").putExtra("message", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "ReservationService"
        private const val NOTIFICATION_ID = 1
    }
}