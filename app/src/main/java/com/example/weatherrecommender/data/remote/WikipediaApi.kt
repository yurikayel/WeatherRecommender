package com.example.weatherrecommender.data.remote

import com.example.weatherrecommender.data.remote.dto.WikipediaSummaryDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Wikipedia REST page summary — free, no API key.
 *
 * Used only to enrich city detail with a thumbnail + extract. Open-Meteo / Nominatim do not
 * provide images. Callers must send a descriptive [User-Agent] per Wikimedia API etiquette.
 */
interface WikipediaApi {
    @GET("page/summary/{title}")
    suspend fun getPageSummary(
        @Path(value = "title", encoded = true) title: String,
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Header("Accept") accept: String = "application/json"
    ): WikipediaSummaryDto

    companion object {
        const val BASE_URL = "https://en.wikipedia.org/api/rest_v1/"
        const val USER_AGENT =
            "WeatherRecommender/1.0 (Concierge take-home; Android; contact: local-dev)"
    }
}
