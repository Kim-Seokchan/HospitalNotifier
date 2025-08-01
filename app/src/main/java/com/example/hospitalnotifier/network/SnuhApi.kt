package com.example.hospitalnotifier.network

import com.example.hospitalnotifier.ScheduleResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface SnuhApi {
    @FormUrlEncoded
    @POST("reservation/reservation.do")
    suspend fun checkAvailability(
        @Header("Cookie") sessionCookie: String,
        @Field("deptCd") deptCd: String,
        @Field("drCd") drCd: String,
        @Field("nextDt") nextDt: String
    ): Response<ScheduleResponse>
}
