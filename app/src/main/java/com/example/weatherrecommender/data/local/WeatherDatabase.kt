package com.example.weatherrecommender.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.local.entity.DailyForecastEntity
import com.example.weatherrecommender.data.local.entity.LocationEntity

/**
 * The Room database for the application.
 * Contains tables for locations and daily forecasts.
 */
@Database(entities = [LocationEntity::class, DailyForecastEntity::class], version = 2, exportSchema = true)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao

    companion object {
        /**
         * v2 adds [DailyForecastEntity.weatherCode] and [DailyForecastEntity.snowfallSum].
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_forecast_entity ADD COLUMN weatherCode INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE daily_forecast_entity ADD COLUMN snowfallSum REAL NOT NULL DEFAULT 0.0"
                )
            }
        }
    }
}
