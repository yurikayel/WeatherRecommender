package com.example.weatherrecommender.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenFreeMapStyleTest {

    @Test
    fun lightThemeUsesLiberty_darkThemeUsesDark() {
        assertEquals(OPENFREEMAP_LIBERTY, openFreeMapStyleUri(darkTheme = false))
        assertEquals(OPENFREEMAP_DARK, openFreeMapStyleUri(darkTheme = true))
    }
}
