package com.example.hospitalnotifier.network

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface SnuhLoginApi {
    @GET("login.do")
    suspend fun initSession(): String

    @FormUrlEncoded
    @POST("loginProc.do")
    suspend fun login(
        @Field("id") id: String,
        @Field("pass") password: String,
        @Field("retUrl") retUrl: String = ""
    ): String
}
