package com.example.hospitalnotifier

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.Observer
import androidx.work.*
import com.example.hospitalnotifier.databinding.ActivityMainBinding
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
        setupWebView()
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true

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
                }

                view.evaluateJavascript("(function() { return document.getElementsByTagName('html')[0].innerHTML; })();") {
                    html ->
                    val pageContent = html.replace("\\u003C", "<")

                    if (pageContent.contains("로그아웃")) {
                        isLoginProcessing = false
                        // binding.webView.visibility = View.GONE // Hide WebView for debugging
                        appendLog("로그인 성공! (로그아웃 버튼 확인)")
                        val cookies = CookieManager.getInstance().getCookie(url)
                        if (cookies != null) {
                            saveLoginData(cookies)
                            startWork()
                        } else {
                            appendLog("오류: 쿠키를 가져오지 못했습니다.")
                            Toast.makeText(this@MainActivity, "쿠키 가져오기 실패", Toast.LENGTH_SHORT).show()
                        }
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
        val interval = binding.spinnerInterval.selectedItem as Float
        val workRequest = PeriodicWorkRequestBuilder<ReservationWorker>(
            (interval * 60).toLong(), TimeUnit.SECONDS
        ).addTag(WORK_TAG).build()
        workManager.enqueueUniquePeriodicWork(WORK_TAG, ExistingPeriodicWorkPolicy.UPDATE, workRequest)
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
            val logMessage = workInfo.outputData.getString("log")
            if (!logMessage.isNullOrBlank()) {
                appendLog("작업자: $logMessage")
            }
            if (workInfo.state == WorkInfo.State.FAILED) {
                val errorMessage = workInfo.outputData.getString("error") ?: "알 수 없는 오류"
                appendLog("작업 실패: $errorMessage")
                Toast.makeText(this, "작업 실패: $errorMessage", Toast.LENGTH_LONG).show()
            }
        })
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