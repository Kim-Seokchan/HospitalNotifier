package com.example.hospitalnotifier

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.hospitalnotifier.databinding.ActivityMainBinding
import com.example.hospitalnotifier.network.ApiClient

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("message")?.let {
                appendLog(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermission()

        setupSpinner()
        setupClickListeners()
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, IntentFilter("log-message"))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
    }

    private fun setupSpinner() {
        val intervals = arrayOf(1f, 5f, 10f, 15f, 30f, 60f)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInterval.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.buttonStart.setOnClickListener { startReservationService() }
        binding.buttonStop.setOnClickListener { stopReservationService() }
    }

    private fun startReservationService() {
        val id = binding.editTextId.text.toString()
        val password = binding.editTextPassword.text.toString()
        if (id.isBlank() || password.isBlank()) {
            Toast.makeText(this, "ID와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (saveLoginData(id, password)) {
            val serviceIntent = Intent(this, ReservationService::class.java).apply {
                putExtra("interval", (binding.spinnerInterval.selectedItem as Float).toLong())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "예약 조회를 시작합니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "조회할 월 입력을 확인하세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveLoginData(id: String, password: String): Boolean {
        val rawMonths = binding.editTextTargetMonths.text.toString()
        val months = rawMonths.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (months.isEmpty()) {
            Toast.makeText(this, "조회할 년-월을 입력해주세요.", Toast.LENGTH_SHORT).show()
            appendLog("targetMonths 입력 없음")
            return false
        }

        val normalizedMonths = mutableListOf<String>()
        val regex = Regex("^(\\d{4})-(\\d{1,2})")
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
                    return false
                }
            } else {
                Toast.makeText(this, "${m} 은(는) YYYY-MM 형식이 아닙니다.", Toast.LENGTH_LONG).show()
                appendLog("잘못된 월 입력: $m")
                return false
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
        return true
    }

    private fun stopReservationService() {
        val serviceIntent = Intent(this, ReservationService::class.java)
        stopService(serviceIntent)

        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        val cookieJar = ApiClient.getOkHttpClient().cookieJar
        if (cookieJar is MyCookieJar) {
            cookieJar.clear()
        }

        binding.editTextId.text.clear()
        binding.editTextPassword.text.clear()
        binding.editTextTargetMonths.text.clear()
        binding.editTextTelegramToken.text.clear()
        binding.editTextTelegramChatId.text.clear()
        binding.spinnerInterval.setSelection(0)
        binding.textViewLog.text = ""

        Toast.makeText(this, "모든 작업과 데이터를 초기화했습니다.", Toast.LENGTH_SHORT).show()
        appendLog("중지 버튼 클릭: 모든 작업과 데이터를 초기화했습니다.")
    }

    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        binding.textViewLog.append("\n[" + timestamp + "] " + message)
        binding.logScrollView.post { binding.logScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(this, "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "알림 권한이 거부되었습니다. 서비스 알림이 표시되지 않을 수 있습니다.", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}