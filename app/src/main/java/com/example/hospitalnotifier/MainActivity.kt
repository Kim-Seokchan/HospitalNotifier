package com.example.hospitalnotifier

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.hospitalnotifier.databinding.ActivityMainBinding
import com.example.hospitalnotifier.worker.ReservationWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // activity_main.xml의 UI 요소들을 제어하기 위한 변수
    private lateinit var binding: ActivityMainBinding
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // "예약 확인 시작" 버튼을 눌렀을 때
        binding.startButton.setOnClickListener {
            val userId = binding.userIdEditText.text.toString()
            val userPw = binding.userPwEditText.text.toString()
            val telegramToken = binding.telegramTokenEditText.text.toString()
            val telegramChatId = binding.telegramChatIdEditText.text.toString()

            if (userId.isEmpty() || userPw.isEmpty() || telegramToken.isEmpty() || telegramChatId.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Worker에게 전달할 데이터 꾸러미 만들기
            val inputData = Data.Builder()
                .putString("USER_ID", userId)
                .putString("USER_PW", userPw)
                .putString("TELEGRAM_TOKEN", telegramToken)
                .putString("TELEGRAM_CHAT_ID", telegramChatId)
                .build()

            // 15분마다 반복되는 작업 요청 생성 (WorkManager의 최소 반복 간격은 15분)
            val reservationWorkRequest =
                PeriodicWorkRequestBuilder<ReservationWorker>(15, TimeUnit.MINUTES)
                    .setInputData(inputData)
                    .build()

            // 중복 실행을 막기 위해 고유한 이름으로 작업을 큐에 추가
            workManager.enqueueUniquePeriodicWork(
                "HospitalReservationCheck",
                ExistingPeriodicWorkPolicy.UPDATE, // 이미 작업이 있다면 새 것으로 교체
                reservationWorkRequest
            )

            binding.statusTextView.text = "확인 작업이 시작되었습니다. 15분마다 백그라운드에서 실행됩니다."
            Toast.makeText(this, "예약 확인을 시작합니다.", Toast.LENGTH_SHORT).show()
        }

        // "중지" 버튼을 눌렀을 때
        binding.stopButton.setOnClickListener {
            workManager.cancelUniqueWork("HospitalReservationCheck")
            binding.statusTextView.text = "대기 중..."
            Toast.makeText(this, "예약 확인을 중지합니다.", Toast.LENGTH_SHORT).show()
        }
    }
}