package com.example.weatherrecommender.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
