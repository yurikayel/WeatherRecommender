package com.example.weatherrecommender.data.mapper

import com.example.weatherrecommender.data.remote.dto.WikipediaSummaryDto
import com.example.weatherrecommender.domain.model.PlaceMedia
import java.net.URLEncoder

/**
 * Pure helpers for Wikipedia place-media enrichment: title candidates + DTO → [PlaceMedia].
 */
object WikipediaPlaceMediaMapper {

    /**
     * Ordered page titles to try for [name] / [country]. Prefer the bare city name first,
     * then "City, Country" for disambiguation.
     */
    fun candidateTitles(name: String, country: String?): List<String> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return emptyList()

        val titles = linkedSetOf(trimmedName)
        val trimmedCountry = country?.trim().orEmpty()
        if (trimmedCountry.isNotEmpty()) {
            titles += "$trimmedName, $trimmedCountry"
        }
        return titles.toList()
    }

    /** Path-encodes a Wikipedia title (spaces → `%20`). */
    fun encodeTitle(title: String): String =
        // String charset name is API 1+; Charset overload requires API 33 (lint NewApi).
        URLEncoder.encode(title, "UTF-8").replace("+", "%20")

    /**
     * Maps a summary payload to [PlaceMedia], or null when the page is unusable
     * (disambiguation / no extract and no thumbnail).
     */
    fun toPlaceMedia(dto: WikipediaSummaryDto): PlaceMedia? {
        val isDisambiguation = dto.type.equals(TYPE_DISAMBIGUATION, ignoreCase = true)
        val imageUrl = dto.thumbnail?.source?.takeIf { it.isNotBlank() }
        val extract = dto.extract?.trim()?.takeIf { it.isNotEmpty() }
        val attribution = listOfNotNull(
            dto.title?.takeIf { it.isNotBlank() },
            dto.displayTitle?.takeIf { it.isNotBlank() }
        ).firstOrNull()

        return when {
            isDisambiguation -> null
            imageUrl == null && extract == null -> null
            else -> PlaceMedia(
                imageUrl = imageUrl,
                description = extract,
                attribution = attribution
            )
        }
    }

    private const val TYPE_DISAMBIGUATION = "disambiguation"
}
