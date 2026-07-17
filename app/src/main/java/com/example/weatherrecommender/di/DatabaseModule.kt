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

    @Provides
    @Singleton
    fun provideWeatherDatabase(@ApplicationContext context: Context): WeatherDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            WeatherDatabase::class.java,
            "weather_database"
        )
            .addMigrations(WeatherDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideWeatherDao(database: WeatherDatabase): WeatherDao {
        return database.weatherDao()
    }
}
