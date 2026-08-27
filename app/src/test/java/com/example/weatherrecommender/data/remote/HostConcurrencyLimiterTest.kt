package com.example.weatherrecommender.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HostConcurrencyLimiterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `matching host serializes calls to max in-flight`() {
        server.enqueue(delayedOk())
        server.enqueue(delayedOk())
        val client = clientFor(maxInFlight = 1, hostSuffix = server.url("/").host)
        val executor = Executors.newFixedThreadPool(2)

        val started = System.nanoTime()
        val first = executor.submit { executeGet(client).close() }
        val second = executor.submit { executeGet(client).close() }
        first.get(5, TimeUnit.SECONDS)
        second.get(5, TimeUnit.SECONDS)
        executor.shutdown()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertEquals(2, server.requestCount)
        assertTrue(
            "expected sequential delays, was ${elapsedMs}ms",
            elapsedMs >= BODY_DELAY_MS + BODY_DELAY_MS / 2
        )
    }

    @Test
    fun `non-matching host is not gated`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val client = clientFor(maxInFlight = 1, hostSuffix = "open-meteo.com")

        executeGet(client).close()

        assertEquals(1, server.requestCount)
    }

    private fun clientFor(maxInFlight: Int, hostSuffix: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HostConcurrencyLimiter(maxInFlight, hostSuffix))
            .build()

    private fun executeGet(client: OkHttpClient) = client.newCall(
        Request.Builder().url(server.url("/")).get().build()
    ).execute()

    private fun delayedOk(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody("ok")
        .setHeadersDelay(BODY_DELAY_MS, TimeUnit.MILLISECONDS)

    private companion object {
        const val BODY_DELAY_MS = 250L
    }
}
