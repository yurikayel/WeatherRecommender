package com.example.weatherrecommender.data.remote

import com.example.weatherrecommender.data.remote.dto.WikipediaResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * Wikimedia Action API for fetching city images.
 * Used to fetch the main "postcard" image for a location.
 * [MediaWiki API](https://www.mediawiki.org/wiki/API:Main_page)
 */
interface WikipediaApi {
    @Headers("User-Agent: WeatherRecommender/1.0 (https://github.com/example/weatherrecommender)")
    @GET("w/api.php")
    suspend fun getPageImage(
        @Query("titles") titles: String,
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "pageimages",
        @Query("format") format: String = "json",
        @Query("piprop") piprop: String = "thumbnail",
        @Query("pithumbsize") pithumbsize: Int = 800
    ): WikipediaResponse

    companion object {
        const val BASE_URL = "https://en.wikipedia.org/"
    }
}
