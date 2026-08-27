package com.example.weatherrecommender.ui

import com.example.weatherrecommender.domain.location.DeviceLocationProvider
import com.example.weatherrecommender.domain.location.GeoCoordinates
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeCycleTest {

    @Test
    fun locationFixOrNull_skipsLookupWithoutPermission() = runTest {
        val provider = mockk<DeviceLocationProvider>()
        every { provider.hasLocationPermission() } returns false
        assertNull(locationFixOrNull(provider))
    }

    @Test
    fun locationFixOrNull_returnsLastKnownWhenPermitted() = runTest {
        val provider = mockk<DeviceLocationProvider>()
        val london = GeoCoordinates(51.5074, -0.1278)
        every { provider.hasLocationPermission() } returns true
        coEvery { provider.getLastKnownLocation() } returns london
        assertEquals(london, locationFixOrNull(provider))
    }
}
