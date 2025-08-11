package com.example.hospitalnotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import com.example.hospitalnotifier.network.ApiClient
import com.example.hospitalnotifier.network.SnuhLoginApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MyCookieJarTest {
    @Test
    fun `loginProc sets JSESSIONID1`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "JSESSIONID1=test; Path=/")
        )
        server.start()

        try {
            val cookieJar = MyCookieJar(context)
            val client = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build()

            val request = Request.Builder()
                .url(server.url("/loginProc.do"))
                .post(byteArrayOf().toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            assertEquals(200, response.code)

            val key = "${server.hostName}|/|JSESSIONID1"
            assertNotNull(prefs.getString(key, null))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `startLoginProcess fails without session cookie`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cookiePrefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
        cookiePrefs.edit().putString("dummy", "value").apply()
        val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        settingsPrefs.edit().putString("id", "id").putString("password", "pass").apply()

        val loginApi = mockk<SnuhLoginApi>()
        coEvery { loginApi.initSession() } returns ""
        coEvery { loginApi.login(any(), any(), any()) } returns ""

        mockkObject(ApiClient)
        every { ApiClient.getLoginApi(any()) } returns loginApi

        val worker = TestListenableWorkerBuilder<ReservationWorker>(context).build()
        val result = worker.startLoginProcess("id", "pass")
        assertTrue(result is Result.Failure)
        assertTrue(cookiePrefs.all.isEmpty())
        assertTrue(settingsPrefs.getString("id", null) == null)
        assertTrue(settingsPrefs.getString("password", null) == null)

        unmockkAll()
    }

    @Test
    fun `startLoginProcess succeeds with session cookie`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cookiePrefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
        cookiePrefs.edit().clear().apply()

        val loginApi = mockk<SnuhLoginApi>()
        coEvery { loginApi.initSession() } returns ""
        coEvery { loginApi.login(any(), any(), any()) } answers {
            cookiePrefs.edit().putString("www.snuh.org|/|JSESSIONID2", "JSESSIONID2=test; Path=/").apply()
            ""
        }

        mockkObject(ApiClient)
        every { ApiClient.getLoginApi(any()) } returns loginApi

        val worker = TestListenableWorkerBuilder<ReservationWorker>(context).build()
        val result = worker.startLoginProcess("id", "pass")
        assertTrue(result is Result.Success)
        assertNotNull(cookiePrefs.all.keys.firstOrNull { it.contains("JSESSIONID2") })

        unmockkAll()
    }
}
