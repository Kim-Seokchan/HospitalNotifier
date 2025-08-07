package com.example.hospitalnotifier

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.example.hospitalnotifier.databinding.ActivityMainBinding
import com.example.hospitalnotifier.network.TelegramClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }
    private var isLoginProcessing = false

    companion object {
        const val WORK_TAG = "hospitalReservationCheck"
        private const val TAG = "MainActivity"
    }

    private val reservationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ReservationWorker.ACTION_CHECK_RESERVATION) {
                checkReservationInWebView()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupClickListeners()
        observeWork()
        setupWebView()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            reservationReceiver,
            IntentFilter(ReservationWorker.ACTION_CHECK_RESERVATION)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(reservationReceiver)
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

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.addJavascriptInterface(WebAppInterface(this), "Android")

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (message != null && isLoginProcessing) {
                    isLoginProcessing = false
                    binding.webView.visibility = View.GONE // Hide WebView
                    val logMessage = "로그인 실패: $message"
                    appendLog(logMessage)
                    Toast.makeText(this@MainActivity, logMessage, Toast.LENGTH_LONG).show()
                }
                result?.confirm()
                return true
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view == null || url == null || !isLoginProcessing) return

                Log.d(TAG, "onPageFinished: $url")
                appendLog("WebView 페이지 로드 완료: $url")

                if (url.contains("login.do")) {
                    val id = binding.editTextId.text.toString()
                    val password = binding.editTextPassword.text.toString()
                    val script = "javascript:document.getElementById('id').value = '$id';" +
                                 "document.getElementById('pass').value = '$password';" +
                                 "document.getElementById('loginBtn').click();"
                    appendLog("로그인 스크립트 실행 시도")
                    view.evaluateJavascript(script, null)
                } else if (url.contains("main.do")) { // 로그인 성공 후 메인 페이지로 이동 시
                    isLoginProcessing = false
                    binding.webView.visibility = View.GONE // Hide WebView
                    appendLog("로그인 성공! (메인 페이지 이동 확인)")
                    val cookies = CookieManager.getInstance().getCookie(url)
                    if (cookies != null) {
                        saveLoginData(cookies)
                        startWork() // 주기적 작업 시작
                    } else {
                        appendLog("오류: 쿠키를 가져오지 못했습니다.")
                        Toast.makeText(this@MainActivity, "쿠키 가져오기 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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
        binding.webView.visibility = View.VISIBLE // Show WebView for login process
        appendLog("로그인 시도 중... (WebView 로딩 시작)")
        binding.webView.loadUrl("https://www.snuh.org/login.do")
    }

    private fun saveLoginData(cookies: String) {
        val targetMonths = binding.editTextTargetMonths.text.toString()
        val interval = binding.spinnerInterval.selectedItem as Float
        val telegramToken = binding.editTextTelegramToken.text.toString()
        val telegramChatId = binding.editTextTelegramChatId.text.toString()
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        sharedPref.edit(commit = true) {
            putString("sessionCookie", cookies)
            putString("targetMonths", targetMonths)
            putFloat("interval", interval)
            putString("telegramToken", telegramToken)
            putString("telegramChatId", telegramChatId)
        }
        appendLog("세션 쿠키를 저장했습니다.")
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

        Toast.makeText(this, "로그인 성공. 예약 조회를 시작합니다.", Toast.LENGTH_SHORT).show()
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
                val finalLog = workInfo.outputData.getString("log")
                if (!finalLog.isNullOrBlank()) {
                    appendLog("작업자: $finalLog")
                }
            }
        })
    }

    private fun checkReservationInWebView() {
        val targetMonths = binding.editTextTargetMonths.text.toString()
        if (targetMonths.isBlank()) {
            appendLog("조회할 월 정보가 없습니다.")
            return
        }

        try {
            val scriptTemplate = assets.open("js/reservation_checker.js").bufferedReader().use { it.readText() }
            val finalScript = scriptTemplate.replace("__TARGET_MONTHS__", targetMonths)
            binding.webView.evaluateJavascript(finalScript, null)
            appendLog("WebView에서 예약 확인을 시작합니다...")
        } catch (e: Exception) {
            appendLog("오류: 스크립트 파일을 읽을 수 없습니다. ${e.message}")
        }
    }

    class WebAppInterface(private val context: Context) {
        @JavascriptInterface
        fun processResult(result: String) {
            (context as MainActivity).runOnUiThread {
                context.appendLog("WebView 예약 확인 결과: $result")
                val gson = Gson()
                val type = object : TypeToken<List<String>>() {}.type
                val availableDates: List<String> = gson.fromJson(result, type)

                if (availableDates.isNotEmpty()) {
                    val distinctDates = availableDates.distinct().sorted()
                    val message = """🎉 예약 가능한 날짜를 찾았습니다! 🎉

${distinctDates.joinToString("\n") { "- $it" }}

[지금 바로 예약하기](https://www.snuh.org/reservation/reservation.do)"""
                    context.sendTelegramMessage(message)
                }
            }
        }
    }

    fun sendTelegramMessage(text: String) {
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        val telegramToken = sharedPref.getString("telegramToken", null)
        val telegramChatId = sharedPref.getString("telegramChatId", null)

        if (telegramToken.isNullOrBlank() || telegramChatId.isNullOrBlank()) {
            appendLog("텔레그램 토큰 또는 챗 ID가 없어 메시지를 발송하지 않습니다.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = TelegramClient.api.sendMessage(
                    token = "bot$telegramToken",
                    chatId = telegramChatId,
                    text = text,
                    parseMode = "Markdown"
                )
                if (response.isSuccessful) {
                    runOnUiThread { appendLog("텔레그램 메시지 발송 성공") }
                } else {
                    runOnUiThread { appendLog("텔레그램 메시지 발송 실패: ${response.code()} ${response.errorBody()?.string()}") }
                }
            } catch (e: Exception) {
                runOnUiThread { appendLog("텔레그램 발송 중 오류 발생: ${e.message}") }
                Log.e(TAG, "텔레그램 발송 중 오류 발생", e)
            }
        }
    }

    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        binding.textViewLog.append("\n[$timestamp] $message")
        Log.d(TAG, message)

        // Automatically scroll to the bottom
        binding.logScrollView.post {
            binding.logScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}