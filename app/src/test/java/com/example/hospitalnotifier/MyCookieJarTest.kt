package com.example.hospitalnotifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MyCookieJarTest {
    @Test
    fun `loginProc sets JSESSIONID1`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cookieJar = MyCookieJar(context)

        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()

        val request = Request.Builder()
            .url("https://www.snuh.org/loginProc.do")
            .post(byteArrayOf().toRequestBody())
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)

        val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
        assertNotNull(prefs.getString("JSESSIONID1", null))
    }
}
