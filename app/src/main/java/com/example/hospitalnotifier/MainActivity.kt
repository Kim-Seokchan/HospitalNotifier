package com.example.hospitalnotifier

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.Observer
import androidx.work.ExistingPeriodicWorkPolicy
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
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }
    private var isLoginProcessing = false

    companion object {
        const val WORK_TAG = "hospitalReservationCheck"
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupClickListeners()
        observeWork()
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
                runOnUiThread {
                    isLoginProcessing = false
                    appendLog("로그인 시도 결과: $response")
                    saveLoginData(id, password)
                    startWork()
                    Toast.makeText(this@MainActivity, "로그인 성공. 예약 조회를 시작합니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
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

    private fun startWork() {
        val intervalMinutes = binding.spinnerInterval.selectedItem as Float
        val intervalMillis = (intervalMinutes * 60 * 1000).toLong()

        val workRequest = PeriodicWorkRequestBuilder<ReservationWorker>(
            intervalMillis, TimeUnit.MILLISECONDS
        )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(WORK_TAG, ExistingPeriodicWorkPolicy.REPLACE, workRequest)
        appendLog("백그라운드 예약 조회를 시작합니다.")
    }

    private fun stopWork() {
        workManager.cancelAllWorkByTag(WORK_TAG)
        Toast.makeText(this, "예약 조회를 중지합니다.", Toast.LENGTH_SHORT).show()
        appendLog("예약 조회를 중지합니다.")
    }

    private fun observeWork() {
        workManager.getWorkInfosByTagLiveData(WORK_TAG).observe(this, Observer { workInfos ->
            if (workInfos.isNullOrEmpty()) return@Observer

            val workInfo = workInfos[0]
            if (workInfo.state.isFinished) {
                appendLog("작업이 완료되었습니다.")
            }
        })
    }

    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        binding.textViewLog.append("\n[$timestamp] $message")
        binding.logScrollView.post { binding.logScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }
}
