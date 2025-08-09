package com.example.hospitalnotifier

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.hospitalnotifier.databinding.ActivityMainBinding
import com.example.hospitalnotifier.network.ApiClient
import com.example.hospitalnotifier.network.SessionManager
import com.example.hospitalnotifier.repository.ReservationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: ReservationRepository
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        val apiService = ApiClient.create(sessionManager)
        repository = ReservationRepository(apiService, sessionManager)

        binding.intervalPicker.minValue = 1
        binding.intervalPicker.maxValue = 60
        binding.intervalPicker.value = 15

        binding.startButton.setOnClickListener {
            val userId = binding.userIdEditText.text.toString()
            val userPw = binding.userPwEditText.text.toString()
            val telegramToken = binding.telegramTokenEditText.text.toString()
            val telegramChatId = binding.telegramChatIdEditText.text.toString()
            val monthsInput = binding.monthsEditText.text.toString()
            val months = monthsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val intervalMinutes = binding.intervalPicker.value

            if (userId.isBlank() || userPw.isBlank() || telegramToken.isBlank() || telegramChatId.isBlank() || months.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sessionManager.saveCredentials(userId, userPw)

            job?.cancel()
            job = lifecycleScope.launch {
                while (isActive) {
                    try {
                        repository.checkAndNotify(months, telegramToken, telegramChatId)
                        binding.statusTextView.text = "확인 성공"
                    } catch (e: Exception) {
                        Log.e("MainActivity", "오류: ${e.message}")
                        binding.statusTextView.text = "오류 발생"
                    }
                    delay(intervalMinutes * 60 * 1000L)
                }
            }
            binding.statusTextView.text = "확인 작업이 시작되었습니다."
            Toast.makeText(this, "예약 확인을 시작합니다.", Toast.LENGTH_SHORT).show()
        }

        binding.stopButton.setOnClickListener {
            job?.cancel()
            binding.statusTextView.text = "대기 중..."
            Toast.makeText(this, "예약 확인을 중지합니다.", Toast.LENGTH_SHORT).show()
        }
    }
}
