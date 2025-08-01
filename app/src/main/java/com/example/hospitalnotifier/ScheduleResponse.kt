package com.example.hospitalnotifier

import com.google.gson.annotations.SerializedName

data class ScheduleResponse(
    @SerializedName("scheduleList")
    val scheduleList: List<Schedule>?
)

data class Schedule(
    @SerializedName("meddate")
    val meddate: String?
)
