package com.example.hospitalnotifier.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException

/**
 * Verifies that [SharedPrefsCookieJar] persists cookies from login responses
 * and automatically attaches them to subsequent requests. It also confirms
 * that cookies can be refreshed by performing a new login after an
 * unauthorized response.
 */
@RunWith(RobolectricTestRunner::class)
class SharedPrefsCookieJarTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun cookieIsPersistedAndRefreshed() = runBlocking {
        val api = ApiClient.create(context, server.url("/").toString())

        // First login returns initial cookie
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "session=first"))
        assertTrue(api.login("id", "pw").isSuccessful)
        server.takeRequest() // consume login request

        // First availability check uses cookie=first but gets 401
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            api.checkAvailability("dept", "dr", "20250101")
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
        val firstCheck = server.takeRequest()
        assertEquals("session=first", firstCheck.getHeader("Cookie"))

        // Re-login with new cookie
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "session=second"))
        assertTrue(api.login("id", "pw").isSuccessful)
        server.takeRequest() // consume second login

        // Final availability check should carry refreshed cookie
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"scheduleList\":[]}"))
        api.checkAvailability("dept", "dr", "20250101")
        val secondCheck = server.takeRequest()
        assertEquals("session=second", secondCheck.getHeader("Cookie"))
    }
}
