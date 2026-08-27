package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyCitiesTest {

    @Test
    fun `selects populated neighbors inside the map radius`() {
        val curitiba = MajorCities.all.first { it.name == "Curitiba" }
        val nearby = NearbyCities.select(curitiba, MajorCities.all)

        val names = nearby.map { it.name }
        assertTrue(names.contains("Joinville"))
        assertTrue(names.contains("Ponta Grossa"))
        assertEquals(false, names.contains("Curitiba"))
        assertEquals(false, names.contains("São Paulo"))
    }

    @Test
    fun `select keeps one row when the same place has two synthetic ids`() {
        val curitiba = MajorCities.all.first { it.name == "Curitiba" }
        val joinville = MajorCities.all.first { it.name == "Joinville" }
        val duplicate = joinville.copy(id = -999)
        val nearby = NearbyCities.select(curitiba, listOf(joinville, duplicate))

        assertEquals(1, nearby.count { it.placeKey == joinville.placeKey })
    }

    @Test
    fun `haversine is symmetric and zero for the same point`() {
        val a = Location(1, "A", -25.4, -49.3, "Brazil", "Paraná")
        val b = Location(2, "B", -26.3, -48.8, "Brazil", "Santa Catarina")
        assertEquals(0.0, NearbyCities.haversineKm(a, a), 0.0001)
        assertEquals(NearbyCities.haversineKm(a, b), NearbyCities.haversineKm(b, a), 0.0001)
    }
}
