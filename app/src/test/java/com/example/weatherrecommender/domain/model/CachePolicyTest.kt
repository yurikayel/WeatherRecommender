package com.example.weatherrecommender.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePolicyTest {

    @Test
    fun placeMetadata_zeroTimestampIsStaleEvenWithUrl() {
        assertFalse(
            CachePolicy.isPlaceMetadataFresh(
                cachedUrl = "https://example.com/london.jpg",
                metadataAt = 0L,
                now = 1_000L
            )
        )
    }

    @Test
    fun placeMetadata_missingUrlIsStale() {
        assertFalse(
            CachePolicy.isPlaceMetadataFresh(
                cachedUrl = null,
                metadataAt = 50L,
                now = 100L
            )
        )
    }

    @Test
    fun placeMetadata_recentTimestampIsFresh() {
        val now = CachePolicy.PLACE_METADATA_TTL_MS
        assertTrue(
            CachePolicy.isPlaceMetadataFresh(
                cachedUrl = "https://example.com/london.jpg",
                metadataAt = now - 1,
                now = now
            )
        )
    }

    @Test
    fun placeMetadata_expiredTimestampIsStale() {
        val now = CachePolicy.PLACE_METADATA_TTL_MS + 10
        assertFalse(
            CachePolicy.isPlaceMetadataFresh(
                cachedUrl = "https://example.com/london.jpg",
                metadataAt = 1L,
                now = now
            )
        )
    }
}
