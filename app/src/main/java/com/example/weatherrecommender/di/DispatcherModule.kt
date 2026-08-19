package com.example.weatherrecommender.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/** Qualifier for the IO dispatcher used by GPS, share bitmap, and other blocking work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Supplies injectable coroutine dispatchers so callers are not hardcoded to [Dispatchers.IO]. */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    /** [Dispatchers.IO] for disk, GPS, and bitmap work off the main thread. */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
