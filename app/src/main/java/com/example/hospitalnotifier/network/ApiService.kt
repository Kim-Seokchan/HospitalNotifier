package com.example.hospitalnotifier.network

import okhttp3.ResponseBody
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
    // 로그인 페이지 로딩 (CSRF 토큰 및 초기 쿠키 추출용)
    @GET("/login.do")
    suspend fun fetchLoginPage(): Response<ResponseBody>

    // 로그인 요청 (필수 파라미터와 헤더를 함께 전달)
    @FormUrlEncoded
    @POST("/login.do")
    suspend fun login(
        @Header("Cookie") cookie: String,
        @Field("id") userId: String,
        @Field("pass") userPw: String,
        @Field("csrfToken") csrfToken: String
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

