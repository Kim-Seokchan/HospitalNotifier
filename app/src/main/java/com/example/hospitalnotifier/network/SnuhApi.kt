package com.example.hospitalnotifier.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SnuhApi {
    @GET("reservation/medDateListAjax.do") // GET으로 변경
    suspend fun checkAvailability(
        @Header("Cookie") sessionCookie: String,
        @Query("hsp_cd") hspCd: String,
        @Query("dept_cd") deptCd: String,
        @Query("dr_cd") drCd: String,
        @Query("nextDt") nextDt: String
    ): String
}
