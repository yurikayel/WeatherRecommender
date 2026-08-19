package com.example.weatherrecommender.domain.util

import java.net.URLEncoder

/**
 * Canonical English Wikipedia article URLs from a page title (city name or API title).
 *
 * Titles are the same strings we send to the MediaWiki Action API (`titles=`). Spaces become
 * underscores; other characters are percent-encoded. Returns null when there is no title yet.
 */
object WikipediaUrls {
    private const val EN_ARTICLE_BASE = "https://en.wikipedia.org/wiki/"

    fun articleUrl(title: String?): String? {
        val normalized = title?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        val path = URLEncoder.encode(normalized.replace(' ', '_'), Charsets.UTF_8.name())
            .replace("+", "_")
        return EN_ARTICLE_BASE + path
    }
}
