package com.example.hospitalnotifier

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class ReservationWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val ACTION_CHECK_RESERVATION = "com.example.hospitalnotifier.ACTION_CHECK_RESERVATION"
    }

    override suspend fun doWork(): Result {
        val log = "예약 확인 시간. MainActivity로 브로드캐스트 전송"
        val intent = Intent(ACTION_CHECK_RESERVATION)
        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
        return Result.success(workDataOf("log" to log))
    }
}
