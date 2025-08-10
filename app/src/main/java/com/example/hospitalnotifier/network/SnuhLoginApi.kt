package com.example.hospitalnotifier.network

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface SnuhLoginApi {
    @FormUrlEncoded
    @POST("loginProc.do")
    suspend fun login(
        @Field("id") id: String,
        @Field("pass") password: String
    ): String
}
