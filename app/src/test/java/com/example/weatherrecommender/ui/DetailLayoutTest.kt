package com.example.weatherrecommender.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailLayoutTest {

    @Test
    fun heroKeeps16by9WhenBodyFits() {
        val width = 1080f
        val height = 1400f
        val minBody = 312f
        val hero = detailHeroHeightPx(width, height, minBody)
        assertEquals(width / (16f / 9f), hero, 0.01f)
    }

    @Test
    fun heroShrinksWhen16by9WouldClipRows() {
        val width = 360f
        val height = 384f
        val minBody = 312f
        val hero = detailHeroHeightPx(width, height, minBody)
        val aspect = width / (16f / 9f)
        val floor = height * DetailLayout.MinHeroFraction
        assertEquals(floor, hero, 0.01f)
        assertTrue(hero < aspect)
    }

    @Test
    fun heroNeverGoesBelowMinimumFraction() {
        val width = 640f
        val height = 200f
        val minBody = 312f
        val hero = detailHeroHeightPx(width, height, minBody)
        assertEquals(height * DetailLayout.MinHeroFraction, hero, 0.01f)
    }
}
