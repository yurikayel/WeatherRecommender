package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class WikipediaResponse(
    val query: WikipediaQuery? = null
)

@Serializable
data class WikipediaQuery(
    val pages: Map<String, WikipediaPage>? = null
)

@Serializable
data class WikipediaPage(
    val pageid: Long? = null,
    val title: String? = null,
    val thumbnail: WikipediaImage? = null,
    val original: WikipediaImage? = null
)

@Serializable
data class WikipediaImage(
    val source: String
)
