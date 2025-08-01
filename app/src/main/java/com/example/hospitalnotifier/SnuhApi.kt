package com.example.hospitalnotifier

import retrofit2.Response
import retrofit2.http.*

interface SnuhApi {

    @GET("/reservation/medDateListAjax.do")
    suspend fun checkReservation(
        @Query("dept_cd") deptCd: String,
        @Query("dr_cd") drCd: String,
        @Query("nextDt") nextDt: String // YYYYMMDD
    ): Response<String> // 예약 정보는 JSON 형태의 String으로 받음
}
