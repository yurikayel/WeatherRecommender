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
    /**
     * Fetches the lead thumbnail (and original fallback) for Wikipedia page [titles].
     *
     * [redirects] `1` follows soft redirects (e.g. `La Habana` → `Havana`) so pageimages
     * returns the target article's photo instead of an empty redirect stub.
     * [pithumbsize] is the requested pixel width of the thumbnail.
     */
    @Headers("User-Agent: WeatherRecommender/1.0 (https://github.com/yurikayel/WeatherRecommender)")
    @GET("w/api.php")
    suspend fun getPageImage(
        @Query("titles") titles: String,
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "pageimages",
        @Query("format") format: String = "json",
        @Query("piprop") piprop: String = "thumbnail|original",
        @Query("pithumbsize") pithumbsize: Int = 800,
        @Query("redirects") redirects: Int = 1
    ): WikipediaResponse

    /**
     * Full-text search for the best English Wikipedia title matching [srsearch]
     * (e.g. `La Habana Cuba` → `Havana`).
     */
    @Headers("User-Agent: WeatherRecommender/1.0 (https://github.com/yurikayel/WeatherRecommender)")
    @GET("w/api.php")
    suspend fun searchPages(
        @Query("srsearch") srsearch: String,
        @Query("action") action: String = "query",
        @Query("list") list: String = "search",
        @Query("format") format: String = "json",
        @Query("srlimit") srlimit: Int = 3
    ): WikipediaResponse

    companion object {
        const val BASE_URL = "https://en.wikipedia.org/"
    }
}
