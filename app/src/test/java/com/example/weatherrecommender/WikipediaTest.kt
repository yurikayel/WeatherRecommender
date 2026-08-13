package com.example.weatherrecommender

import com.example.weatherrecommender.data.remote.WikipediaApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class WikipediaTest {
    @Test
    fun testWiki() = runBlocking {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(WikipediaApi.BASE_URL)
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            
        val api = retrofit.create(WikipediaApi::class.java)
        try {
            val response = api.getPageImage("Barcelona")
            println("Success: " + response.query?.pages?.values?.firstOrNull()?.original?.source)
        } catch(e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
