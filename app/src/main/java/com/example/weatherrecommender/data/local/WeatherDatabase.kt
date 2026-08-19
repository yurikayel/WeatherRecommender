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
@Database(entities = [LocationEntity::class, DailyForecastEntity::class], version = 8, exportSchema = true)
abstract class WeatherDatabase : RoomDatabase() {
    /**
     * Retrieves the primary [WeatherDao] for executing database transactions.
     */
    abstract fun weatherDao(): WeatherDao

    companion object {
        /**
         * v2 adds [DailyForecastEntity.weatherCode] and [DailyForecastEntity.snowfallSum].
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            /** Adds weather-code and snowfall columns used by per-day activity scoring. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_forecast_entity ADD COLUMN weatherCode INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE daily_forecast_entity ADD COLUMN snowfallSum REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        /**
         * v3 adds geography metadata to locations (elevation, population, featureCode, hasSeaAccess)
         * and per-day marine [DailyForecastEntity.waveHeightMax], enabling geography-aware activities.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            /** Adds location geography fields and per-day wave height for sea-access detection. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE location_entity ADD COLUMN elevation REAL")
                db.execSQL("ALTER TABLE location_entity ADD COLUMN population INTEGER")
                db.execSQL("ALTER TABLE location_entity ADD COLUMN featureCode TEXT")
                db.execSQL(
                    "ALTER TABLE location_entity ADD COLUMN hasSeaAccess INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE daily_forecast_entity ADD COLUMN waveHeightMax REAL")
            }
        }

        /**
         * v4 adds [LocationEntity.lastViewedAt] so the home History section can order cities by
         * explicit user selection, independent of forecast [LocationEntity.lastUpdated] sync age.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            /** Adds last-viewed timestamp so History can order by explicit user opens. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE location_entity ADD COLUMN lastViewedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v5 briefly cached Wikipedia place media columns. Kept so devices that reached v5 can
         * migrate forward; [MIGRATION_5_6] drops those columns.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            /** Adds the short-lived Wikipedia media columns later dropped in v6. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE location_entity ADD COLUMN imageUrl TEXT")
                db.execSQL("ALTER TABLE location_entity ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE location_entity ADD COLUMN imageAttribution TEXT")
            }
        }

        /**
         * v6 removes Wikipedia place-media columns (imageUrl, description, imageAttribution).
         * Recreates the table (SQLite DROP COLUMN is unavailable on older Android SQLite).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            /** Recreates `location_entity` without the v5 Wikipedia columns (no DROP COLUMN). */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(LOCATION_V6_CREATE)
                db.execSQL(LOCATION_V6_COPY)
                db.execSQL("DROP TABLE location_entity")
                db.execSQL("ALTER TABLE location_entity_new RENAME TO location_entity")
            }
        }
        /**
         * v7 re-adds [LocationEntity.imageUrl] for the postcard UI.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            /** Restores a nullable thumbnail URL for postcard cards. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE location_entity ADD COLUMN imageUrl TEXT")
            }
        }

        /**
         * v8 adds [LocationEntity.placeMetadataUpdatedAt] so Wikipedia thumbnails and names
         * can be reused for 30 days independently of forecast [LocationEntity.lastUpdated].
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            /** Adds place-metadata timestamp so Wikipedia reuse is independent of forecast TTL. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE location_entity ADD COLUMN placeMetadataUpdatedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private const val LOCATION_V6_CREATE = """
                    CREATE TABLE IF NOT EXISTS location_entity_new (
                        id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        country TEXT NOT NULL,
                        admin1 TEXT,
                        lastUpdated INTEGER NOT NULL,
                        elevation REAL,
                        population INTEGER,
                        featureCode TEXT,
                        hasSeaAccess INTEGER NOT NULL,
                        lastViewedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """

        private const val LOCATION_V6_COPY = """
                    INSERT INTO location_entity_new (
                        id, name, latitude, longitude, country, admin1, lastUpdated,
                        elevation, population, featureCode, hasSeaAccess, lastViewedAt
                    )
                    SELECT
                        id, name, latitude, longitude, country, admin1, lastUpdated,
                        elevation, population, featureCode, hasSeaAccess, lastViewedAt
                    FROM location_entity
                    """
    }
}
