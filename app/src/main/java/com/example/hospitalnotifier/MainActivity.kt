package com.example.hospitalnotifier

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.hospitalnotifier.databinding.ActivityMainBinding
import com.example.hospitalnotifier.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isLoginProcessing = false
    private val scope = MainScope()
    private var reservationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupClickListeners()
    }

    private fun setupSpinner() {
        val intervals = arrayOf(0.5f, 1f, 5f, 10f, 15f)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInterval.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.buttonStart.setOnClickListener { startLoginProcess() }
        binding.buttonStop.setOnClickListener { stopWork() }
    }

    private fun startLoginProcess() {
        if (isLoginProcessing) {
            Toast.makeText(this, "이미 로그인이 진행 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val id = binding.editTextId.text.toString()
        val password = binding.editTextPassword.text.toString()
        if (id.isBlank() || password.isBlank()) {
            Toast.makeText(this, "ID와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        isLoginProcessing = true
        appendLog("로그인 시도 중...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.getLoginApi(this@MainActivity).login(id, password)
                if (!response.contains("SUCCESS")) {
                    Log.e(TAG, "로그인 실패 응답: $response")
                    runOnUiThread {
                        isLoginProcessing = false
                        appendLog("로그인 실패: $response")
                        Toast.makeText(
                            this@MainActivity,
                            "로그인 실패: $response",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                runOnUiThread {
                    isLoginProcessing = false
                    appendLog("로그인 성공")
                    saveLoginData(id, password)
                    startPeriodicCheck()
                    Toast.makeText(
                        this@MainActivity,
                        "로그인 성공. 예약 조회를 시작합니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "로그인 실패: ${e.message}")
                runOnUiThread {
                    isLoginProcessing = false
                    appendLog("로그인 실패: ${e.message}")
                    Toast.makeText(this@MainActivity, "로그인 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveLoginData(id: String, password: String) {
        val targetMonths = binding.editTextTargetMonths.text.toString()
        val interval = binding.spinnerInterval.selectedItem as Float
        val telegramToken = binding.editTextTelegramToken.text.toString()
        val telegramChatId = binding.editTextTelegramChatId.text.toString()
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        sharedPref.edit(commit = true) {
            putString("id", id)
            putString("password", password)
            putString("targetMonths", targetMonths)
            putFloat("interval", interval)
            putString("telegramToken", telegramToken)
            putString("telegramChatId", telegramChatId)
        }
        appendLog("ID와 비밀번호를 포함한 로그인 정보를 저장했습니다.")
    }

    private fun startPeriodicCheck() {
        val intervalMinutes = binding.spinnerInterval.selectedItem as Float
        reservationJob?.cancel()
        reservationJob = scope.launch {
            while (isActive) {
                checkReservationInWebView()
                delay((intervalMinutes * 60_000).toLong())
            }
        }
        appendLog("예약 조회를 시작합니다.")
    }

    private fun stopWork() {
        reservationJob?.cancel()
        Toast.makeText(this, "예약 조회를 중지합니다.", Toast.LENGTH_SHORT).show()
        appendLog("예약 조회를 중지합니다.")
    }

    private suspend fun checkReservationInWebView() {
        // TODO: 웹뷰에서 예약 확인 로직 구현
    }

    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        binding.textViewLog.append("\n[$timestamp] $message")
        binding.logScrollView.post { binding.logScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
