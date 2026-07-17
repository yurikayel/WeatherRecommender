package com.example.weatherrecommender.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RateLimitRetryInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(RateLimitRetryInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `retries GET on 429 until success`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = executeGet()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
        response.close()
    }

    @Test
    fun `does not retry non-GET requests`() {
        server.enqueue(MockResponse().setResponseCode(429))

        val request = Request.Builder()
            .url(server.url("/"))
            .post(ByteArray(0).toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(429, response.code)
        assertEquals(1, server.requestCount)
        response.close()
    }

    @Test
    fun `returns 429 after max retries exhausted`() {
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0"))
        }

        val response = executeGet()

        assertEquals(429, response.code)
        assertEquals(4, server.requestCount)
        response.close()
    }

    private fun executeGet(): Response {
        val request = Request.Builder().url(server.url("/")).get().build()
        return client.newCall(request).execute()
    }
}
