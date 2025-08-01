package com.example.hospitalnotifier

import retrofit2.Response
import retrofit2.http.*

interface SnuhApi {

    @GET("/login.do")
    suspend fun getLoginPage(): Response<String>

    @FormUrlEncoded
    @POST("/login/loginProc.do")
    suspend fun login(
        @Field("hsp_cd") hspCd: String = "1",
        @Field("user_id") userId: String,
        @Field("user_pw") userPw: String
    ): Response<String> // 로그인 성공 시 HTML 페이지의 내용을 String으로 받음

    @Headers(
        "Referer: https://www.snuh.org/reservation/reservation.do",
        "X-Requested-With: XMLHttpRequest"
    )
    @GET("/reservation/medDateListAjax.do")
    suspend fun checkReservation(
        @Query("dept_cd") deptCd: String,
        @Query("dr_cd") drCd: String,
        @Query("nextDt") nextDt: String // YYYYMMDD
    ): Response<String> // 예약 정보는 JSON 형태의 String으로 받음
}