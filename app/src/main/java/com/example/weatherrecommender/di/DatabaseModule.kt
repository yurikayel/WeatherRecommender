package com.example.weatherrecommender.di

import android.content.Context
import androidx.room.Room
import com.example.weatherrecommender.data.local.WeatherDatabase
import com.example.weatherrecommender.data.local.dao.WeatherDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing Room database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Builds the singleton Room database with v1–v8 migrations and no destructive fallback. */
    @Provides
    @Singleton
    fun provideWeatherDatabase(@ApplicationContext context: Context): WeatherDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            WeatherDatabase::class.java,
            "weather_database"
        )
            .addMigrations(
                WeatherDatabase.MIGRATION_1_2,
                WeatherDatabase.MIGRATION_2_3,
                WeatherDatabase.MIGRATION_3_4,
                WeatherDatabase.MIGRATION_4_5,
                WeatherDatabase.MIGRATION_5_6,
                WeatherDatabase.MIGRATION_6_7,
                WeatherDatabase.MIGRATION_7_8
            )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    /** Exposes the database's [WeatherDao] to the rest of the graph. */
    @Provides
    fun provideWeatherDao(database: WeatherDatabase): WeatherDao {
        return database.weatherDao()
    }
}
