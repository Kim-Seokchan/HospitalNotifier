package com.example.hospitalnotifier

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.work.*
import android.util.Log 
import com.example.hospitalnotifier.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }

    companion object {
        const val WORK_TAG = "hospitalReservationCheck"
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
        binding.buttonStart.setOnClickListener { startWork() }
        binding.buttonStop.setOnClickListener { stopWork() }
    }

    private fun startWork() {
        val id = binding.editTextId.text.toString()
        val password = binding.editTextPassword.text.toString()
        val targetMonths = binding.editTextTargetMonths.text.toString()
        val interval = binding.spinnerInterval.selectedItem as Float
        val telegramToken = binding.editTextTelegramToken.text.toString()
        val telegramChatId = binding.editTextTelegramChatId.text.toString()

        if (id.isBlank() || password.isBlank() || targetMonths.isBlank()) {
            Toast.makeText(this, "ID, 비밀번호, 조회 월은 필수입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // Save user input to SharedPreferences
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("id", id)
            putString("password", password)
            putString("targetMonths", targetMonths)
            putFloat("interval", interval)
            putString("telegramToken", telegramToken)
            putString("telegramChatId", telegramChatId)
            apply()
        }

        val workRequest = PeriodicWorkRequestBuilder<ReservationWorker>(
            interval.toLong(), TimeUnit.MINUTES
        )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )

        Toast.makeText(this, "예약 조회를 시작합니다.", Toast.LENGTH_SHORT).show()
        val logMessage = "\n[${java.util.Date()}] 예약 조회를 시작합니다."
        binding.textViewLog.append(logMessage)
    }

    private fun stopWork() {
        workManager.cancelAllWorkByTag(WORK_TAG)
        Toast.makeText(this, "예약 조회를 중지합니다.", Toast.LENGTH_SHORT).show()
        val logMessage = "\n[${java.util.Date()}] 예약 조회를 중지합니다."
        binding.textViewLog.append(logMessage)
    }

    private fun observeWork() {
        workManager.getWorkInfosByTagLiveData(WORK_TAG).observe(this, Observer { workInfos ->
            if (workInfos.isNullOrEmpty()) {
                return@Observer
            }
            val workInfo = workInfos[0]

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val successMessage = workInfo.outputData.getString("message") ?: "작업 성공."
                    val logMessage = "\n[${java.util.Date()}] $successMessage"
                    binding.textViewLog.append(logMessage)
                }
                WorkInfo.State.FAILED -> {
                    val errorMessage = workInfo.outputData.getString("error") ?: "알 수 없는 오류"
                    val logMessage = "\n[${java.util.Date()}] 작업 실패: $errorMessage"
                    binding.textViewLog.append(logMessage)
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    stopWork() // 실패 시 작업 중지
                }
                WorkInfo.State.CANCELLED -> {
                    val logMessage = "\n[${java.util.Date()}] 작업 취소됨."
                    binding.textViewLog.append(logMessage)
                }
                else -> {
                    val logMessage = "\n[${java.util.Date()}] 작업 진행 중..."
                    binding.textViewLog.append(logMessage)
                }
            }
        })
    }
}