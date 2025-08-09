package com.example.hospitalnotifier.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 로그인 절차를 담당하는 리포지토리.
 * 1) 로그인 페이지를 불러 CSRF 토큰과 초기 쿠키를 얻는다.
 * 2) 해당 토큰과 함께 로그인 요청을 보내고 세션 쿠키를 반환한다.
 */
object AuthRepository {
    private const val TAG = "AuthRepository"

    /**
     * 로그인 시도 후 세션 쿠키를 반환한다. 실패 시 예외를 발생시킨다.
     */
    suspend fun login(userId: String, userPw: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                // 1. 로그인 페이지 조회
                val pageResponse = ApiClient.instance.fetchLoginPage()
                val initialCookie = pageResponse.headers().values("Set-Cookie").joinToString("; ")
                val html = pageResponse.body()?.string().orEmpty()

                // CSRF 토큰 파싱 (일반적으로 name="csrfToken" 또는 name="_csrf")
                val csrfToken = Regex("name=\"csrfToken\" value=\"([^\"]+)\"")
                    .find(html)?.groupValues?.get(1)
                    ?: Regex("name=\"_csrf\" value=\"([^\"]+)\"")
                        .find(html)?.groupValues?.get(1)

                if (csrfToken.isNullOrEmpty()) {
                    Log.e(TAG, "CSRF 토큰 파싱 실패")
                    return@withContext Result.failure(Exception("Token parsing failed"))
                }

                // 2. 로그인 요청
                val loginResponse = ApiClient.instance.login(initialCookie, userId, userPw, csrfToken)
                if (loginResponse.code() in 300..399) {
                    Log.e(TAG, "Unexpected redirect: ${loginResponse.headers()["Location"]}")
                }

                if (!loginResponse.isSuccessful) {
                    Log.e(TAG, "로그인 실패: ${loginResponse.code()}")
                    return@withContext Result.failure(Exception("Login failed"))
                }

                val sessionCookie = loginResponse.headers().values("Set-Cookie").joinToString("; ")
                if (sessionCookie.isEmpty()) {
                    Log.e(TAG, "세션 쿠키 추출 실패")
                    return@withContext Result.failure(Exception("No session cookie"))
                }

                Result.success(sessionCookie)
            } catch (e: Exception) {
                Log.e(TAG, "로그인 오류: ${e.message}")
                Result.failure(e)
            }
        }
}

