package com.example.weatherrecommender.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        WeatherDatabase::class.java
    )

    @Test
    fun migrate1To2_addsWeatherCodeAndSnowfallColumns() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS location_entity (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    country TEXT NOT NULL,
                    admin1 TEXT,
                    lastUpdated INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_forecast_entity (
                    locationId INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    maxTemp REAL NOT NULL,
                    minTemp REAL NOT NULL,
                    precipitationSum REAL NOT NULL,
                    maxWindSpeed REAL NOT NULL,
                    PRIMARY KEY(locationId, date)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO location_entity (id, name, latitude, longitude, country, admin1, lastUpdated)
                VALUES (1, 'London', 51.5, -0.1, 'UK', 'England', 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO daily_forecast_entity (
                    locationId, date, maxTemp, minTemp, precipitationSum, maxWindSpeed
                ) VALUES (1, '2026-07-16', 22.0, 12.0, 0.0, 10.0)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            false,
            WeatherDatabase.MIGRATION_1_2
        )

        db.query(
            "SELECT weatherCode, snowfallSum FROM daily_forecast_entity WHERE locationId = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getColumnIndex("weatherCode") >= 0)
            assertTrue(cursor.getColumnIndex("snowfallSum") >= 0)
        }
        db.close()
    }

    @Test
    fun migrate2To3_addsGeographyAndWaveColumns() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS location_entity (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    country TEXT NOT NULL,
                    admin1 TEXT,
                    lastUpdated INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_forecast_entity (
                    locationId INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    maxTemp REAL NOT NULL,
                    minTemp REAL NOT NULL,
                    weatherCode INTEGER NOT NULL DEFAULT 0,
                    precipitationSum REAL NOT NULL,
                    maxWindSpeed REAL NOT NULL,
                    snowfallSum REAL NOT NULL DEFAULT 0.0,
                    PRIMARY KEY(locationId, date)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO location_entity (id, name, latitude, longitude, country, admin1, lastUpdated)
                VALUES (1, 'Lisbon', 38.7, -9.1, 'Portugal', 'Lisbon', 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO daily_forecast_entity (
                    locationId, date, maxTemp, minTemp, weatherCode, precipitationSum, maxWindSpeed, snowfallSum
                ) VALUES (1, '2026-07-16', 27.0, 19.0, 0, 0.0, 10.0, 0.0)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            false,
            WeatherDatabase.MIGRATION_2_3
        )

        db.query(
            "SELECT elevation, population, featureCode, hasSeaAccess FROM location_entity WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getColumnIndex("elevation") >= 0)
            assertTrue(cursor.getColumnIndex("population") >= 0)
            assertTrue(cursor.getColumnIndex("featureCode") >= 0)
            assertTrue(cursor.getColumnIndex("hasSeaAccess") >= 0)
        }
        db.query(
            "SELECT waveHeightMax FROM daily_forecast_entity WHERE locationId = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getColumnIndex("waveHeightMax") >= 0)
        }
        db.close()
    }

    @Test
    fun migrate3To4_addsLastViewedAtColumn() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS location_entity (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    country TEXT NOT NULL,
                    admin1 TEXT,
                    lastUpdated INTEGER NOT NULL,
                    elevation REAL,
                    population INTEGER,
                    featureCode TEXT,
                    hasSeaAccess INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_forecast_entity (
                    locationId INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    maxTemp REAL NOT NULL,
                    minTemp REAL NOT NULL,
                    weatherCode INTEGER NOT NULL DEFAULT 0,
                    precipitationSum REAL NOT NULL,
                    maxWindSpeed REAL NOT NULL,
                    snowfallSum REAL NOT NULL DEFAULT 0.0,
                    waveHeightMax REAL,
                    PRIMARY KEY(locationId, date)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO location_entity (
                    id, name, latitude, longitude, country, admin1, lastUpdated, hasSeaAccess
                ) VALUES (1, 'Lisbon', 38.7, -9.1, 'Portugal', 'Lisbon', 1, 1)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            false,
            WeatherDatabase.MIGRATION_3_4
        )

        db.query(
            "SELECT lastViewedAt FROM location_entity WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getColumnIndex("lastViewedAt") >= 0)
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("lastViewedAt")))
        }
        db.close()
    }

    @Test
    fun migrate4To5_addsPlaceMediaColumns() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS location_entity (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    country TEXT NOT NULL,
                    admin1 TEXT,
                    lastUpdated INTEGER NOT NULL,
                    elevation REAL,
                    population INTEGER,
                    featureCode TEXT,
                    hasSeaAccess INTEGER NOT NULL DEFAULT 0,
                    lastViewedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_forecast_entity (
                    locationId INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    maxTemp REAL NOT NULL,
                    minTemp REAL NOT NULL,
                    weatherCode INTEGER NOT NULL DEFAULT 0,
                    precipitationSum REAL NOT NULL,
                    maxWindSpeed REAL NOT NULL,
                    snowfallSum REAL NOT NULL DEFAULT 0.0,
                    waveHeightMax REAL,
                    PRIMARY KEY(locationId, date)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO location_entity (
                    id, name, latitude, longitude, country, admin1, lastUpdated, hasSeaAccess, lastViewedAt
                ) VALUES (1, 'Lisbon', 38.7, -9.1, 'Portugal', 'Lisbon', 1, 1, 99)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            false,
            WeatherDatabase.MIGRATION_4_5
        )

        db.query(
            "SELECT imageUrl, description, imageAttribution, lastViewedAt FROM location_entity WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getColumnIndex("imageUrl") >= 0)
            assertTrue(cursor.getColumnIndex("description") >= 0)
            assertTrue(cursor.getColumnIndex("imageAttribution") >= 0)
            assertEquals(99L, cursor.getLong(cursor.getColumnIndexOrThrow("lastViewedAt")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("imageUrl")))
        }
        db.close()
    }

    @Test
    fun migrate5To6_removesPlaceMediaColumns() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS location_entity (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    country TEXT NOT NULL,
                    admin1 TEXT,
                    lastUpdated INTEGER NOT NULL,
                    elevation REAL,
                    population INTEGER,
                    featureCode TEXT,
                    hasSeaAccess INTEGER NOT NULL DEFAULT 0,
                    lastViewedAt INTEGER NOT NULL DEFAULT 0,
                    imageUrl TEXT,
                    description TEXT,
                    imageAttribution TEXT
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_forecast_entity (
                    locationId INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    maxTemp REAL NOT NULL,
                    minTemp REAL NOT NULL,
                    weatherCode INTEGER NOT NULL DEFAULT 0,
                    precipitationSum REAL NOT NULL,
                    maxWindSpeed REAL NOT NULL,
                    snowfallSum REAL NOT NULL DEFAULT 0.0,
                    waveHeightMax REAL,
                    PRIMARY KEY(locationId, date)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO location_entity (
                    id, name, latitude, longitude, country, admin1, lastUpdated,
                    hasSeaAccess, lastViewedAt, imageUrl, description, imageAttribution
                ) VALUES (
                    1, 'Lisbon', 38.7, -9.1, 'Portugal', 'Lisbon', 1, 1, 99,
                    'https://example.com/x.jpg', 'extract', 'Lisbon'
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            false,
            WeatherDatabase.MIGRATION_5_6
        )

        db.query(
            "SELECT lastViewedAt, name FROM location_entity WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(99L, cursor.getLong(cursor.getColumnIndexOrThrow("lastViewedAt")))
            assertEquals("Lisbon", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertTrue(cursor.getColumnIndex("imageUrl") < 0)
            assertTrue(cursor.getColumnIndex("description") < 0)
            assertTrue(cursor.getColumnIndex("imageAttribution") < 0)
        }
        db.close()
    }

    @Test
    fun migrate7To8_addsPlaceMetadataUpdatedAt() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS location_entity (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    country TEXT NOT NULL,
                    admin1 TEXT,
                    lastUpdated INTEGER NOT NULL,
                    elevation REAL,
                    population INTEGER,
                    featureCode TEXT,
                    hasSeaAccess INTEGER NOT NULL DEFAULT 0,
                    lastViewedAt INTEGER NOT NULL DEFAULT 0,
                    imageUrl TEXT
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_forecast_entity (
                    locationId INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    maxTemp REAL NOT NULL,
                    minTemp REAL NOT NULL,
                    weatherCode INTEGER NOT NULL DEFAULT 0,
                    precipitationSum REAL NOT NULL,
                    maxWindSpeed REAL NOT NULL,
                    snowfallSum REAL NOT NULL DEFAULT 0.0,
                    waveHeightMax REAL,
                    PRIMARY KEY(locationId, date)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO location_entity (
                    id, name, latitude, longitude, country, admin1, lastUpdated,
                    hasSeaAccess, lastViewedAt, imageUrl
                ) VALUES (1, 'Lisbon', 38.7, -9.1, 'Portugal', 'Lisbon', 1, 1, 99, 'https://example.com/x.jpg')
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            false,
            WeatherDatabase.MIGRATION_7_8
        )

        db.query(
            "SELECT placeMetadataUpdatedAt, imageUrl FROM location_entity WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("placeMetadataUpdatedAt")))
            assertEquals("https://example.com/x.jpg", cursor.getString(cursor.getColumnIndexOrThrow("imageUrl")))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
