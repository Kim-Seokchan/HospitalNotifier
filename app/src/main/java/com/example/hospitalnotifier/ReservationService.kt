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
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.TimeUnit

class ReservationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    private var intervalMinutes = 15L

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intervalMinutes = intent?.getLongExtra("interval", 15L) ?: 15L
        startForeground(NOTIFICATION_ID, createNotification())
        startChecking()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }

    private fun createNotification(): android.app.Notification {
        val notificationChannelId = "RESERVATION_SERVICE_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                notificationChannelId,
                "Reservation Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(notificationChannel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Hospital Reservation Checker")
            .setContentText("Checking for available reservations...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startChecking() {
        runnable = Runnable {
            CoroutineScope(Dispatchers.IO).launch {
                checkReservation()
            }
            handler.postDelayed(runnable, TimeUnit.MINUTES.toMillis(intervalMinutes))
        }
        handler.post(runnable)
    }

    private suspend fun checkReservation() {
        log("Starting reservation check...")
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val id = sharedPref.getString("id", null)
        val password = sharedPref.getString("password", null)
        val targetMonths = sharedPref.getString("targetMonths", null)
        val token = sharedPref.getString("telegramToken", null)
        val chatId = sharedPref.getString("telegramChatId", null)

        if (id == null || password == null || targetMonths == null) {
            log("Login information not found. Stopping service.")
            stopSelf()
            return
        }

        try {
            clearCookies()
            val loginSuccess = startLoginProcess(id, password)
            if (!loginSuccess) {
                log("Login failed.")
                return
            }

            val api = ApiClient.getSnuhApi()
            val months = targetMonths.split(",").map { it.trim() }
            val availableDates = mutableListOf<String>()
            for (month in months) {
                log("Querying month: $month")
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
                            log("Session expired. Re-logging in...")
                            clearCookies()
                            val reLogin = startLoginProcess(id, password)
                            if (!reLogin) {
                                log("Re-login failed.")
                                break
                            }
                            attempt++
                            continue
                        } else if (!response.isSuccessful) {
                            val code = response.code()
                            val errorBody = response.errorBody()?.string()
                            log("Reservation check failed: HTTP $code, error: $errorBody")
                            break
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
                        break
                    } catch (e: Exception) {
                        log("Reservation check failed: ${e.message}")
                        if (e is HttpException && (e.code() == 401 || e.code() == 302)) {
                            log("Session expired. Re-logging in...")
                            clearCookies()
                            val reLogin = startLoginProcess(id, password)
                            if (!reLogin) {
                                log("Re-login failed.")
                                break
                            }
                            attempt++
                        } else {
                            break
                        }
                    }
                }
            }

            if (availableDates.isNotEmpty()) {
                val message = "Available dates: ${availableDates.joinToString()}"
                log(message)
                if (!token.isNullOrBlank() && !chatId.isNullOrBlank()) {
                    sendTelegramMessage(token, chatId, message)
                }
            } else {
                log("No available dates found.")
            }
        } catch (e: Exception) {
            log("An error occurred: ${e.message}")
        }
    }

    private suspend fun startLoginProcess(id: String, password: String): Boolean {
        return try {
            val loginApi = ApiClient.getLoginApi()
            loginApi.initSession()
            val response = loginApi.login(id, password)
            if (response.contains("login.do")) {
                log("Login failed: login.do response")
                clearCookies()
                false
            } else {
                val cookieJar = ApiClient.getOkHttpClient().cookieJar as MyCookieJar
                val cookies = cookieJar.getCookies("https://www.snuh.org/")
                val session = cookies.firstOrNull { it.name.startsWith("JSESSIONID") }

                if (session == null) {
                    log("Session cookie not found.")
                    clearCookies()
                    false
                } else {
                    log("Login successful.")
                    true
                }
            }
        } catch (e: Exception) {
            log("Login failed: ${e.message}")
            clearCookies()
            false
        }
    }

    private fun clearCookies() {
        val cookieJar = ApiClient.getOkHttpClient().cookieJar as MyCookieJar
        cookieJar.clear()
    }

    private suspend fun sendTelegramMessage(token: String, chatId: String, text: String) {
        try {
            val response = TelegramClient.api.sendMessage(token, chatId, text)
            if (response.isSuccessful) {
                log("Telegram message sent successfully.")
            } else {
                log("Failed to send Telegram message: ${response.code()}")
            }
        } catch (e: Exception) {
            log("Failed to send Telegram message: ${e.message}")
        }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        val intent = Intent("log-message")
        intent.putExtra("message", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "ReservationService"
        private const val NOTIFICATION_ID = 1
    }
}
