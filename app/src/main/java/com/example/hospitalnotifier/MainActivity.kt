package com.example.hospitalnotifier

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.hospitalnotifier.databinding.ActivityMainBinding
import com.example.hospitalnotifier.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.UUID
import androidx.work.workDataOf
import androidx.work.Data

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isLoginProcessing = false
    private var currentWorkId: UUID? = null
    private val observedIds = mutableSetOf<UUID>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupClickListeners()
        observeWorker()
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
        val id = binding.editTextId.text.toString()
        val password = binding.editTextPassword.text.toString()
        if (id.isBlank() || password.isBlank()) {
            Toast.makeText(this, "ID와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val loginData = saveLoginData(id, password)
        if (loginData != null) {
            startWork(loginData)
            Toast.makeText(this, "예약 조회를 시작합니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "조회할 월 입력을 확인하세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveLoginData(id: String, password: String): Bundle? {
        val rawMonths = binding.editTextTargetMonths.text.toString()
        val months = rawMonths.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (months.isEmpty()) {
            Toast.makeText(this, "조회할 년-월을 입력해주세요.", Toast.LENGTH_SHORT).show()
            appendLog("targetMonths 입력 없음")
            return null
        }

        val normalizedMonths = mutableListOf<String>()
        val regex = Regex("^(\\d{4})-(\\d{1,2})$")
        for (m in months) {
            val match = regex.matchEntire(m)
            if (match != null) {
                val year = match.groupValues[1]
                val monthNum = match.groupValues[2].toInt()
                if (monthNum in 1..12) {
                    normalizedMonths.add("$year-${monthNum.toString().padStart(2, '0')}")
                } else {
                    Toast.makeText(this, "${m} 은(는) 올바른 월이 아닙니다.", Toast.LENGTH_LONG).show()
                    appendLog("잘못된 월 입력: $m")
                    return null
                }
            } else {
                Toast.makeText(this, "${m} 은(는) YYYY-MM 형식이 아닙니다.", Toast.LENGTH_LONG).show()
                appendLog("잘못된 월 입력: $m")
                return null
            }
        }

        val interval = binding.spinnerInterval.selectedItem as Float
        val telegramToken = binding.editTextTelegramToken.text.toString().trim()
        val telegramChatId = binding.editTextTelegramChatId.text.toString().trim()
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        sharedPref.edit(commit = true) {
            putString("id", id)
            putString("password", password)
            putString("targetMonths", normalizedMonths.joinToString(","))
            putFloat("interval", interval)
            putString("telegramToken", telegramToken)
            putString("telegramChatId", telegramChatId)
        }
        appendLog("ID와 비밀번호를 포함한 로그인 정보를 저장했습니다.")
        return Bundle().apply {
            putString("id", id)
            putString("password", password)
            putString("targetMonths", normalizedMonths.joinToString(","))
        }
    }

    private fun startWork(loginData: Bundle) {
        val workManager = WorkManager.getInstance(this)

        val dataMap = loginData.keySet().associateWith { key ->
            loginData.get(key)
        }

        val inputData = Data.Builder()
            .putAll(dataMap)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReservationWorker>()
            .setInputData(inputData)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        appendLog("단일 예약 조회를 시작합니다.")
    }

    private fun stopWork() {
        // WorkManager 작업 취소
        WorkManager.getInstance(this).cancelAllWorkByTag(WORK_TAG)

        // SharedPreferences 데이터 삭제
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        // 쿠키 삭제
        val cookieJar = ApiClient.getOkHttpClient().cookieJar
        if (cookieJar is MyCookieJar) {
            cookieJar.clear()
        }

        // UI 초기화
        binding.editTextId.text.clear()
        binding.editTextPassword.text.clear()
        binding.editTextTargetMonths.text.clear()
        binding.editTextTelegramToken.text.clear()
        binding.editTextTelegramChatId.text.clear()
        binding.spinnerInterval.setSelection(0)
        binding.textViewLog.text = ""

        // 내부 상태 초기화
        currentWorkId = null
        observedIds.clear()

        Toast.makeText(this, "모든 작업과 데이터를 초기화했습니다.", Toast.LENGTH_SHORT).show()
        appendLog("중지 버튼 클릭: 모든 작업과 데이터를 초기화했습니다.")
    }

    private fun observeWorker() {
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(WORK_TAG)
            .observe(this) { workInfos ->
                workInfos
                    .filter { it.state.isFinished }
                    .filterNot { observedIds.contains(it.id) }
                    .forEach { info ->
                        val status = info.outputData.getString("status")
                            ?: info.progress.getString("status")
                        status?.let { appendLog(it) }
                        observedIds.add(info.id)
                    }

                val runningInfo = workInfos
                    .filter { it.state == WorkInfo.State.RUNNING }
                    .filterNot { observedIds.contains(it.id) }
                    .lastOrNull()

                runningInfo?.let { info ->
                    currentWorkId = info.id
                    val status = info.progress.getString("status")
                    status?.let { appendLog(it) }
                }
            }
    }

    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        binding.textViewLog.append("\n[$timestamp] $message")
        binding.logScrollView.post { binding.logScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val WORK_TAG = "reservationWork"
    }
}