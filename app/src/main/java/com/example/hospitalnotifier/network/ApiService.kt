package com.example.hospitalnotifier.network

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// JSON 응답을 담을 그릇(데이터 클래스)
data class ScheduleResponse(val scheduleList: List<ScheduleItem>?)
data class ScheduleItem(val meddate: String?)

// 어떤 주소로 통신할지 정의하는 메뉴판
interface ApiService {
    // 로그인 요청 (실제 요청 경로는 병원 웹사이트에 맞게 수정 필요)
    @FormUrlEncoded
    @POST("/login.do")
    suspend fun login(
        @Field("id") userId: String,
        @Field("pass") userPw: String
    ): Response<Void>

    // 예약 가능일 확인 요청
    @GET("/reservation/medDateListAjax.do")
    suspend fun checkAvailability(
        @Header("Cookie") sessionCookie: String,
        @Query("dept_cd") deptCd: String,
        @Query("dr_cd") drCd: String,
        @Query("nextDt") nextDt: String
    ): ScheduleResponse
}