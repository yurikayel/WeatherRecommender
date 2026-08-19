package com.example.weatherrecommender.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.weatherrecommender.domain.location.DeviceLocationProvider
import com.example.weatherrecommender.domain.location.GeoCoordinates
import com.example.weatherrecommender.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DeviceLocationProvider] backed by [LocationManager.getLastKnownLocation].
 *
 * Choice vs FusedLocationProvider: avoids the Play Services location artifact while
 * still covering GPS + network + passive providers. Last-known can be null on devices
 * that have never obtained a fix — callers must fall back (e.g. static London default).
 */
@Singleton
class AndroidDeviceLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : DeviceLocationProvider {

    /** Fine or coarse location is granted. */
    override fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** Best last-known fix among GPS, network, and passive providers, or null. */
    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): GeoCoordinates? = withContext(ioDispatcher) {
        if (!hasLocationPermission()) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let { GeoCoordinates(it.latitude, it.longitude) }
    }
}
