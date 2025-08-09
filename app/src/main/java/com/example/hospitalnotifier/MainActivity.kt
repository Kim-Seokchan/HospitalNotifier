package com.example.hospitalnotifier

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.hospitalnotifier.databinding.ActivityMainBinding
import com.example.hospitalnotifier.network.ApiClient
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // activity_main.xml의 UI 요소들을 제어하기 위한 변수
    private lateinit var binding: ActivityMainBinding
    private var reservationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.intervalPicker.minValue = 1
        binding.intervalPicker.maxValue = 60
        binding.intervalPicker.value = 15

        binding.startButton.setOnClickListener {
            val userId = binding.userIdEditText.text.toString()
            val userPw = binding.userPwEditText.text.toString()
            val telegramToken = binding.telegramTokenEditText.text.toString()
            val telegramChatId = binding.telegramChatIdEditText.text.toString()
            val interval = binding.intervalPicker.value

            if (userId.isEmpty() || userPw.isEmpty() || telegramToken.isEmpty() || telegramChatId.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            reservationJob?.cancel()
            reservationJob = lifecycleScope.launch {
                binding.statusTextView.text = "확인 작업이 시작되었습니다. ${interval}분 간격으로 실행됩니다."
                Toast.makeText(this@MainActivity, "예약 확인을 시작합니다.", Toast.LENGTH_SHORT).show()
                while (isActive) {
                    binding.statusTextView.text = "예약 확인 중..."
                    val result = checkReservation(userId, userPw, telegramToken, telegramChatId)
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    binding.statusTextView.text = "마지막 확인 ($time): $result"
                    delay(interval * 60_000L)
                }
            }
            Toast.makeText(this, "앱이 백그라운드에서 종료되면 작업이 중지될 수 있습니다.", Toast.LENGTH_LONG).show()
        }

        binding.stopButton.setOnClickListener {
            reservationJob?.cancel()
            reservationJob = null
            binding.statusTextView.text = "대기 중..."
            Toast.makeText(this, "예약 확인을 중지합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun checkReservation(
        userId: String,
        userPw: String,
        token: String,
        chatId: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val loginResponse = ApiClient.instance.login(userId, userPw)
            if (!loginResponse.isSuccessful) {
                return@withContext "로그인 실패"
            }

            val cookies = loginResponse.headers().values("Set-Cookie")
            val sessionCookie = cookies.joinToString("; ")
            if (sessionCookie.isEmpty()) {
                return@withContext "세션 쿠키 얻기 실패"
            }

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

            return@withContext if (foundDates.isNotEmpty()) {
                val message = "🎉 예약 가능한 날짜를 찾았습니다!\n$foundDates"
                sendTelegramMessage(message, token, chatId)
                "예약 가능 날짜 발견!"
            } else {
                "빈자리 없음."
            }
        } catch (e: Exception) {
            "오류 발생: ${e.message}"
        }
    }

    private suspend fun sendTelegramMessage(message: String, token: String, chatId: String) {
        val url = "https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=$message"
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().close()
            } catch (_: Exception) {
            }
        }
    }
}