package com.example.weatherrecommender.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherScreenMapHeightTest {

    @Test
    fun `home peek offset keeps map at the uncovered pane plus corner leftover`() {
        // 60% of a 2000px scaffold; 24dp corner overlap in px (example 72px @3x).
        val mapHeight = mapLayoutHeightPx(sheetOffsetPx = 1200f, cornerOverlapPx = 72f)
        assertEquals(1272f, mapHeight, 0f)
    }

    @Test
    fun `detail lock offset is 40 percent of the scaffold plus leftover`() {
        val mapHeight = mapLayoutHeightPx(sheetOffsetPx = 800f, cornerOverlapPx = 72f)
        assertEquals(872f, mapHeight, 0f)
    }

    @Test
    fun `expanded sheet leaves only the corner leftover instead of a hidden full-size map`() {
        val mapHeight = mapLayoutHeightPx(sheetOffsetPx = 0f, cornerOverlapPx = 72f)
        assertEquals(72f, mapHeight, 0f)
    }

    @Test
    fun `negative offset is clamped so map height never goes below the leftover`() {
        val mapHeight = mapLayoutHeightPx(sheetOffsetPx = -40f, cornerOverlapPx = 72f)
        assertEquals(72f, mapHeight, 0f)
    }
}
