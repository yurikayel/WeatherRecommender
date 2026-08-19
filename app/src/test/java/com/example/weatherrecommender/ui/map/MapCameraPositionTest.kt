package com.example.weatherrecommender.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapCameraPositionTest {

    @Test
    fun `home city and detail zooms are scaled ~30 percent out from prior defaults`() {
        // Prior values: home 7.0, city 8.5, detail 9.0 → × 0.7
        assertEquals(4.9, MapCameraPosition.HOME_DEFAULT_ZOOM, 0.0)
        assertEquals(6.0, MapCameraPosition.CITY_ZOOM, 0.0)
        assertEquals(6.3, MapCameraPosition.DETAIL_ZOOM, 0.0)
        assertEquals(MapCameraPosition.HOME_DEFAULT_ZOOM, MapCameraPosition.DEFAULT.zoom, 0.0)
        assertEquals(MapHopProfile.CACHE_MISS, MapCameraPosition.DEFAULT.hop)
    }

    @Test
    fun `cache miss hop reveals 500 ms before a 1200 ms flight ends`() {
        assertEquals(350L, MapHopProfile.CACHE_MISS.delayMs)
        assertEquals(1200L, MapHopProfile.CACHE_MISS.durationMs)
        assertEquals(500L, MapHopProfile.CACHE_MISS.leadMs)
        assertEquals(1050L, MapHopProfile.CACHE_MISS.contentRevealMs)
    }

    @Test
    fun `cached hop uses a shorter delay so the 600 ms flight dominates`() {
        assertEquals(150L, MapHopProfile.CACHED.delayMs)
        assertEquals(600L, MapHopProfile.CACHED.durationMs)
        assertEquals(200L, MapHopProfile.CACHED.leadMs)
        assertEquals(550L, MapHopProfile.CACHED.contentRevealMs)
    }
}
