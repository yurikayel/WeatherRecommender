package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.Location

/**
 * Regional hubs used to prefetch neighbors visible at the detail map zoom.
 *
 * Open-Meteo Geocoding has no "nearby cities" endpoint, so a curated list is the reliable way
 * to warm Room for map navigation (e.g. Curitiba → Joinville / Ponta Grossa).
 */
object MajorCities {

    val all: List<Location> = listOf(
        city(-101, "Curitiba", -25.4284, -49.2733, "Brazil", "Paraná", 1_963_726, 935.0),
        city(-102, "Joinville", -26.3045, -48.8487, "Brazil", "Santa Catarina", 597_658, 4.0),
        city(-103, "Ponta Grossa", -25.0916, -50.1668, "Brazil", "Paraná", 358_419, 969.0),
        city(-104, "Florianópolis", -27.5949, -48.5482, "Brazil", "Santa Catarina", 516_524, 3.0),
        city(-105, "Londrina", -23.3045, -51.1696, "Brazil", "Paraná", 575_377, 610.0),
        city(-106, "Porto Alegre", -30.0346, -51.2177, "Brazil", "Rio Grande do Sul", 1_488_252, 10.0),
        city(-107, "São Paulo", -23.5505, -46.6333, "Brazil", "São Paulo", 12_396_372, 760.0),
        city(-108, "Campinas", -22.9099, -47.0626, "Brazil", "São Paulo", 1_223_237, 680.0),
        city(-109, "Rio de Janeiro", -22.9068, -43.1729, "Brazil", "Rio de Janeiro", 6_747_815, 5.0),
        city(-110, "Belo Horizonte", -19.9167, -43.9345, "Brazil", "Minas Gerais", 2_521_564, 852.0),
        city(-111, "Buenos Aires", -34.6037, -58.3816, "Argentina", "Buenos Aires", 3_120_612, 25.0),
        city(-112, "Montevideo", -34.9011, -56.1645, "Uruguay", "Montevideo", 1_380_000, 43.0),
        city(-113, "Santiago", -33.4489, -70.6693, "Chile", "Santiago Metropolitan", 6_257_000, 570.0),
        city(-114, "Lisbon", 38.7223, -9.1393, "Portugal", "Lisbon", 517_802, 68.0),
        city(-115, "Porto", 41.1579, -8.6291, "Portugal", "Porto", 237_591, 104.0),
        city(-116, "Madrid", 40.4168, -3.7038, "Spain", "Madrid", 3_223_334, 667.0),
        city(-117, "Barcelona", 41.3874, 2.1686, "Spain", "Catalonia", 1_620_343, 12.0),
        city(-118, "Valencia", 39.4699, -0.3763, "Spain", "Valencia", 800_215, 15.0),
        city(-119, "Paris", 48.8566, 2.3522, "France", "Île-de-France", 2_138_551, 42.0),
        city(-120, "Lyon", 45.7640, 4.8357, "France", "Auvergne-Rhône-Alpes", 522_969, 173.0),
        city(-121, "London", 51.5074, -0.1278, "United Kingdom", "England", 8_961_989, 25.0),
        city(-122, "Birmingham", 52.4862, -1.8904, "United Kingdom", "England", 1_144_900, 140.0),
        city(-123, "Manchester", 53.4808, -2.2426, "United Kingdom", "England", 547_627, 38.0),
        city(-124, "Amsterdam", 52.3676, 4.9041, "Netherlands", "North Holland", 872_680, 2.0),
        city(-125, "Rotterdam", 51.9244, 4.4777, "Netherlands", "South Holland", 651_446, 0.0),
        city(-126, "Berlin", 52.5200, 13.4050, "Germany", "Berlin", 3_677_472, 34.0),
        city(-127, "Munich", 48.1351, 11.5820, "Germany", "Bavaria", 1_487_708, 520.0),
        city(-128, "Hamburg", 53.5511, 9.9937, "Germany", "Hamburg", 1_851_000, 6.0),
        city(-129, "Rome", 41.9028, 12.4964, "Italy", "Lazio", 2_763_804, 21.0),
        city(-130, "Milan", 45.4642, 9.1900, "Italy", "Lombardy", 1_371_498, 120.0),
        city(-131, "New York", 40.7128, -74.0060, "United States", "New York", 8_335_897, 10.0),
        city(-132, "Philadelphia", 39.9526, -75.1652, "United States", "Pennsylvania", 1_603_797, 12.0),
        city(-133, "Boston", 42.3601, -71.0589, "United States", "Massachusetts", 675_647, 43.0),
        city(-134, "Los Angeles", 34.0522, -118.2437, "United States", "California", 3_898_747, 93.0),
        city(-135, "San Diego", 32.7157, -117.1611, "United States", "California", 1_386_932, 19.0),
        city(-136, "San Francisco", 37.7749, -122.4194, "United States", "California", 873_965, 16.0),
        city(-137, "Denver", 39.7392, -104.9903, "United States", "Colorado", 715_522, 1609.0),
        city(-138, "Tokyo", 35.6895, 139.6917, "Japan", "Tokyo", 8_336_599, 40.0),
        city(-139, "Yokohama", 35.4437, 139.6380, "Japan", "Kanagawa", 3_777_491, 24.0),
        city(-140, "Osaka", 34.6937, 135.5023, "Japan", "Osaka", 2_752_412, 5.0),
        city(-141, "Sydney", -33.8688, 151.2093, "Australia", "New South Wales", 5_231_147, 58.0),
        city(-142, "Melbourne", -37.8136, 144.9631, "Australia", "Victoria", 5_078_193, 31.0),
        city(-143, "Cape Town", -33.9249, 18.4241, "South Africa", "Western Cape", 3_433_441, 25.0)
    )

    private fun city(
        id: Long,
        name: String,
        lat: Double,
        lon: Double,
        country: String,
        admin1: String,
        population: Long,
        elevation: Double
    ) = Location(
        id = id,
        name = name,
        latitude = lat,
        longitude = lon,
        country = country,
        admin1 = admin1,
        elevation = elevation,
        population = population,
        hasSeaAccess = false
    )
}
