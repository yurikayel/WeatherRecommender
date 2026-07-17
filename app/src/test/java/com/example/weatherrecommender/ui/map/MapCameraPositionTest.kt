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
    }
}
