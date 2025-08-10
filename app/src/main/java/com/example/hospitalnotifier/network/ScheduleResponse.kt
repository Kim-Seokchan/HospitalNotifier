package com.example.hospitalnotifier.network

/**
 * Represents the response from the SNUH schedule API.
 */
data class ScheduleResponse(
    val scheduleList: List<ScheduleItem>?
)

/**
 * Represents a schedule item containing the medical date.
 */
data class ScheduleItem(
    val meddate: String?
)

