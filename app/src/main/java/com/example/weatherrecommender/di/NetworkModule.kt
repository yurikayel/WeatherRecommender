package com.example.weatherrecommender.di

import com.example.weatherrecommender.BuildConfig
import com.example.weatherrecommender.data.remote.ForecastApi
import com.example.weatherrecommender.data.remote.GeocodingApi
import com.example.weatherrecommender.data.remote.MarineApi
import com.example.weatherrecommender.data.remote.NominatimApi
import com.example.weatherrecommender.data.remote.WikipediaApi
import com.example.weatherrecommender.data.remote.RateLimitRetryInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for providing network-related dependencies.
 * Configures Retrofit and OkHttp for API communication.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "WeatherRecommender/1.0 (https://github.com/yurikayel/WeatherRecommender)"
                    )
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(RateLimitRetryInterceptor())

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("GeocodingRetrofit")
    fun provideGeocodingRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(GeocodingApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("ForecastRetrofit")
    fun provideForecastRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(ForecastApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("MarineRetrofit")
    fun provideMarineRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(MarineApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("WikipediaRetrofit")
    fun provideWikipediaRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(WikipediaApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideGeocodingApi(@Named("GeocodingRetrofit") retrofit: Retrofit): GeocodingApi {
        return retrofit.create(GeocodingApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWikipediaApi(@Named("WikipediaRetrofit") retrofit: Retrofit): WikipediaApi {
        return retrofit.create(WikipediaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideForecastApi(@Named("ForecastRetrofit") retrofit: Retrofit): ForecastApi {
        return retrofit.create(ForecastApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMarineApi(@Named("MarineRetrofit") retrofit: Retrofit): MarineApi {
        return retrofit.create(MarineApi::class.java)
    }

    @Provides
    @Singleton
    @Named("NominatimRetrofit")
    fun provideNominatimRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NominatimApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideNominatimApi(@Named("NominatimRetrofit") retrofit: Retrofit): NominatimApi {
        return retrofit.create(NominatimApi::class.java)
    }
}
