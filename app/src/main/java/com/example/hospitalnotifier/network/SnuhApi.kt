package com.example.hospitalnotifier.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SnuhApi {
    @GET("reservation/medDateListAjax.do")
    suspend fun checkAvailability(
        @Query("hsp_cd") hspCd: String,
        @Query("dept_cd") deptCd: String,
        @Query("dr_cd") drCd: String,
        @Query("nextDt") nextDt: String
    ): Response<ScheduleResponse>
}
