package com.example.hospitalnotifier

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.hospitalnotifier.databinding.ActivityMainBinding
import com.example.hospitalnotifier.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isLoginProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupClickListeners()
    }

    private fun setupSpinner() {
        val intervals = arrayOf(15f, 30f, 60f)
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
                val loginApi = ApiClient.getLoginApi(this@MainActivity)
                loginApi.initSession()
                val response = loginApi.login(id, password)
                if (response.contains("login.do")) {
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
                    startWork()
                    Toast.makeText(
                        this@MainActivity,
                        "로그인 성공. 예약 조회를 시작합니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "로그인 실패: ${'$'}{e.message}")
                runOnUiThread {
                    isLoginProcessing = false
                    appendLog("로그인 실패: ${'$'}{e.message}")
                    Toast.makeText(this@MainActivity, "로그인 실패: ${'$'}{e.message}", Toast.LENGTH_LONG).show()
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

    private fun startWork() {
        val intervalMinutes = binding.spinnerInterval.selectedItem as Float
        val oneTimeRequest = OneTimeWorkRequestBuilder<ReservationWorker>().build()
        WorkManager.getInstance(this).enqueue(oneTimeRequest)

        val periodicRequest = PeriodicWorkRequestBuilder<ReservationWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reservationWork",
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicRequest
        )

        appendLog("예약 조회를 시작합니다.")
    }

    private fun stopWork() {
        WorkManager.getInstance(this).cancelUniqueWork("reservationWork")
        Toast.makeText(this, "예약 조회를 중지합니다.", Toast.LENGTH_SHORT).show()
        appendLog("예약 조회를 중지합니다.")
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
