package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.Location
import javax.inject.Inject
import kotlin.random.Random

/**
 * Curated world capitals used to frame the home map when device GPS is unavailable.
 *
 * Seed [Location.id] values are **negative** (−100…−N) so they never collide with
 * FeaturedCities (−1…−14), positive GeoNames / Open-Meteo IDs, or Nominatim synthetics
 * (−1_000_000 − placeId).
 */
class WorldCapitals @Inject constructor() {

    val all: List<Location> = listOf(
        capital(-100, "London", 51.5074, -0.1278, "United Kingdom", "England"),
        capital(-101, "Paris", 48.8566, 2.3522, "France", "Île-de-France"),
        capital(-102, "Berlin", 52.5200, 13.4050, "Germany", "Berlin"),
        capital(-103, "Madrid", 40.4168, -3.7038, "Spain", "Madrid"),
        capital(-104, "Rome", 41.9028, 12.4964, "Italy", "Lazio"),
        capital(-105, "Lisbon", 38.7223, -9.1393, "Portugal", "Lisbon"),
        capital(-106, "Amsterdam", 52.3676, 4.9041, "Netherlands", "North Holland"),
        capital(-107, "Brussels", 50.8503, 4.3517, "Belgium", "Brussels"),
        capital(-108, "Vienna", 48.2082, 16.3738, "Austria", "Vienna"),
        capital(-109, "Warsaw", 52.2297, 21.0122, "Poland", "Masovian"),
        capital(-110, "Prague", 50.0755, 14.4378, "Czechia", "Prague"),
        capital(-111, "Athens", 37.9838, 23.7275, "Greece", "Attica"),
        capital(-112, "Stockholm", 59.3293, 18.0686, "Sweden", "Stockholm"),
        capital(-113, "Oslo", 59.9139, 10.7522, "Norway", "Oslo"),
        capital(-114, "Copenhagen", 55.6761, 12.5683, "Denmark", "Capital Region"),
        capital(-115, "Helsinki", 60.1699, 24.9384, "Finland", "Uusimaa"),
        capital(-116, "Dublin", 53.3498, -6.2603, "Ireland", "Leinster"),
        capital(-117, "Reykjavik", 64.1466, -21.9426, "Iceland", "Capital Region"),
        capital(-118, "Moscow", 55.7558, 37.6173, "Russia", "Moscow"),
        capital(-119, "Ankara", 39.9334, 32.8597, "Turkey", "Ankara"),
        capital(-120, "Cairo", 30.0444, 31.2357, "Egypt", "Cairo"),
        capital(-121, "Nairobi", -1.2921, 36.8219, "Kenya", "Nairobi"),
        capital(-122, "Abuja", 9.0765, 7.3986, "Nigeria", "Federal Capital Territory"),
        capital(-123, "Cape Town", -33.9249, 18.4241, "South Africa", "Western Cape"),
        capital(-124, "Addis Ababa", 9.0320, 38.7469, "Ethiopia", "Addis Ababa"),
        capital(-125, "Rabat", 34.0209, -6.8416, "Morocco", "Rabat-Salé-Kénitra"),
        capital(-126, "Washington", 38.9072, -77.0369, "United States", "District of Columbia"),
        capital(-127, "Ottawa", 45.4215, -75.6972, "Canada", "Ontario"),
        capital(-128, "Mexico City", 19.4326, -99.1332, "Mexico", "Mexico City"),
        capital(-129, "Brasília", -15.7975, -47.8919, "Brazil", "Federal District"),
        capital(-130, "Buenos Aires", -34.6037, -58.3816, "Argentina", "Buenos Aires"),
        capital(-131, "Santiago", -33.4489, -70.6693, "Chile", "Santiago Metropolitan"),
        capital(-132, "Lima", -12.0464, -77.0428, "Peru", "Lima"),
        capital(-133, "Bogotá", 4.7110, -74.0721, "Colombia", "Bogotá"),
        capital(-134, "Tokyo", 35.6895, 139.6917, "Japan", "Tokyo"),
        capital(-135, "Seoul", 37.5665, 126.9780, "South Korea", "Seoul"),
        capital(-136, "Beijing", 39.9042, 116.4074, "China", "Beijing"),
        capital(-137, "New Delhi", 28.6139, 77.2090, "India", "Delhi"),
        capital(-138, "Bangkok", 13.7563, 100.5018, "Thailand", "Bangkok"),
        capital(-139, "Jakarta", -6.2088, 106.8456, "Indonesia", "Jakarta"),
        capital(-140, "Canberra", -35.2809, 149.1300, "Australia", "Australian Capital Territory"),
        capital(-141, "Wellington", -41.2865, 174.7762, "New Zealand", "Wellington"),
        capital(-142, "Singapore", 1.3521, 103.8198, "Singapore", "Singapore"),
        capital(-143, "Kuala Lumpur", 3.1390, 101.6869, "Malaysia", "Kuala Lumpur"),
        capital(-144, "Manila", 14.5995, 120.9842, "Philippines", "Metro Manila"),
        capital(-145, "Hanoi", 21.0278, 105.8342, "Vietnam", "Hanoi"),
        capital(-146, "Riyadh", 24.7136, 46.6753, "Saudi Arabia", "Riyadh"),
        capital(-147, "Tehran", 35.6892, 51.3890, "Iran", "Tehran"),
        capital(-148, "Islamabad", 33.6844, 73.0479, "Pakistan", "Islamabad Capital Territory"),
        capital(-149, "Abu Dhabi", 24.4539, 54.3773, "United Arab Emirates", "Abu Dhabi")
    )

    /** Picks one capital uniformly at random. */
    fun random(random: Random = Random.Default): Location =
        all[random.nextInt(all.size)]

    private fun capital(
        id: Long,
        name: String,
        lat: Double,
        lon: Double,
        country: String,
        admin1: String
    ) = Location(
        id = id,
        name = name,
        latitude = lat,
        longitude = lon,
        country = country,
        admin1 = admin1,
        featureCode = "PPLC",
        hasSeaAccess = false
    )
}
