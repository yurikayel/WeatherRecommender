package com.example.weatherrecommender.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WorldCapitalsTest {

    private val capitals = WorldCapitals()

    @Test
    fun `curated list has expected size and capital feature codes`() {
        assertEquals(50, capitals.all.size)
        assertTrue(capitals.all.all { it.featureCode == "PPLC" })
        assertTrue(capitals.all.all { it.id in -149L..-100L })
        assertTrue(capitals.all.map { it.id }.distinct().size == capitals.all.size)
    }

    @Test
    fun `random picker is deterministic for a seeded Random`() {
        val a = capitals.random(Random(42))
        val b = capitals.random(Random(42))
        assertEquals(a, b)
        assertTrue(a in capitals.all)
    }
}
