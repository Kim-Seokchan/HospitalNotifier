package com.example.hospitalnotifier

data class ScheduleResponse(
    val scheduleList: List<Schedule>?
)

data class Schedule(
    val meddate: String?
)