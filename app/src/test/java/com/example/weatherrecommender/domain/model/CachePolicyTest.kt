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

    @Test
    fun shouldFetch_neverConfirmed() {
        assertTrue(CachePolicy.shouldFetchPlaceImage(null, metadataAt = 0L, now = 1_000L))
        assertTrue(
            CachePolicy.shouldFetchPlaceImage(
                cachedUrl = "https://example.com/x.jpg",
                metadataAt = 0L,
                now = 1_000L
            )
        )
    }

    @Test
    fun shouldFetch_skipsFreshHit() {
        val now = CachePolicy.PLACE_METADATA_TTL_MS
        assertFalse(
            CachePolicy.shouldFetchPlaceImage(
                cachedUrl = "https://example.com/london.jpg",
                metadataAt = now - 1,
                now = now
            )
        )
    }

    @Test
    fun shouldFetch_retriesExpiredHit() {
        val now = CachePolicy.PLACE_METADATA_TTL_MS + 10
        assertTrue(
            CachePolicy.shouldFetchPlaceImage(
                cachedUrl = "https://example.com/london.jpg",
                metadataAt = 1L,
                now = now
            )
        )
    }

    @Test
    fun shouldFetch_skipsRecentMiss() {
        val now = CachePolicy.PLACE_METADATA_MISS_TTL_MS
        assertFalse(
            CachePolicy.shouldFetchPlaceImage(
                cachedUrl = null,
                metadataAt = now - 1,
                now = now
            )
        )
    }

    @Test
    fun shouldFetch_retriesExpiredMiss() {
        val now = CachePolicy.PLACE_METADATA_MISS_TTL_MS + 10
        assertTrue(
            CachePolicy.shouldFetchPlaceImage(
                cachedUrl = null,
                metadataAt = 1L,
                now = now
            )
        )
    }
}
