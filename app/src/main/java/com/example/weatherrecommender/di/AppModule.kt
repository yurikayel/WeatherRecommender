package com.example.weatherrecommender.di

import android.content.Context
import com.example.weatherrecommender.data.util.LogCrashReporter
import com.example.weatherrecommender.data.util.NetworkConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.CrashReporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: LogCrashReporter): CrashReporter

    companion object {
        @Provides
        @Singleton
        fun provideConnectivityObserver(@ApplicationContext context: Context): ConnectivityObserver {
            return NetworkConnectivityObserver(context)
        }
    }
}
