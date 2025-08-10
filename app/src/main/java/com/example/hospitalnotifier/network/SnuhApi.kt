package com.example.hospitalnotifier.network

import retrofit2.http.GET
import retrofit2.http.Query

data class ScheduleResponse(val scheduleList: List<ScheduleItem>?)
data class ScheduleItem(val meddate: String?)

interface SnuhApi {
    @GET("reservation/medDateListAjax.do")
    suspend fun checkAvailability(
        @Query("hsp_cd") hspCd: String,
        @Query("dept_cd") deptCd: String,
        @Query("dr_cd") drCd: String,
        @Query("nextDt") nextDt: String
    ): ScheduleResponse
}
