package com.example.weatherrecommender.ui.map

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.Location
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.milliseconds

/**
 * Square map section fixed at the top of [com.example.weatherrecommender.ui.WeatherScreenContent].
 *
 * Camera and pin come from the ViewModel; keeping this outside the home/detail Crossfade avoids
 * remount flash when selecting a city or going back. Under Paparazzi / inspection mode, renders
 * a lightweight placeholder.
 */
@Composable
fun WeatherMapSection(
    camera: MapCameraPosition,
    pin: Location?,
    isResolvingTap: Boolean,
    onMapTap: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val mapCd = stringResource(R.string.map_content_description)
    val attribution = stringResource(R.string.map_attribution)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 280))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .semantics { contentDescription = mapCd }
        ) {
            if (LocalInspectionMode.current) {
                MapPlaceholder(pin = pin)
            } else {
                WeatherMapLibre(
                    camera = camera,
                    pin = pin,
                    onMapTap = onMapTap
                )
            }

            if (isResolvingTap) {
                val resolvingCd = stringResource(R.string.map_resolving_location)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { contentDescription = resolvingCd }
                    )
                }
            }
        }
        Text(
            text = attribution,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .semantics { contentDescription = attribution }
        )
    }
}

@Composable
private fun WeatherMapLibre(
    camera: MapCameraPosition,
    pin: Location?,
    onMapTap: (latitude: Double, longitude: Double) -> Unit
) {
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = camera.longitude, latitude = camera.latitude),
            zoom = camera.zoom
        )
    )

    LaunchedEffect(camera.latitude, camera.longitude, camera.zoom) {
        cameraState.animateTo(
            finalPosition = CameraPosition(
                target = Position(longitude = camera.longitude, latitude = camera.latitude),
                zoom = camera.zoom
            ),
            duration = 450.milliseconds
        )
    }

    val pinGeoJson = remember(pin?.latitude, pin?.longitude) {
        if (pin == null) {
            EMPTY_FEATURE_COLLECTION
        } else {
            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${pin.longitude},${pin.latitude}]},"properties":{}}]}"""
        }
    }

    MaplibreMap(
        modifier = Modifier.fillMaxSize(),
        baseStyle = BaseStyle.Uri(OPENFREEMAP_LIBERTY),
        cameraState = cameraState,
        options = MapOptions(
            gestureOptions = GestureOptions(
                isTiltEnabled = false,
                isZoomEnabled = true,
                isRotateEnabled = false,
                isScrollEnabled = true
            ),
            ornamentOptions = OrnamentOptions.OnlyLogo
        ),
        onMapClick = { position, _ ->
            onMapTap(position.latitude, position.longitude)
            ClickResult.Consume
        },
        onMapLongClick = { position, _ ->
            onMapTap(position.latitude, position.longitude)
            ClickResult.Consume
        }
    ) {
        val source = rememberGeoJsonSource(data = GeoJsonData.JsonString(pinGeoJson))
        CircleLayer(
            id = "selected-city-pin",
            source = source,
            color = const(PIN_COLOR),
            radius = const(8.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp)
        )
    }
}

@Composable
private fun MapPlaceholder(pin: Location?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = pin?.displayName ?: stringResource(R.string.map_placeholder_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private const val OPENFREEMAP_LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
private const val EMPTY_FEATURE_COLLECTION =
    """{"type":"FeatureCollection","features":[]}"""
private val PIN_COLOR = Color(0xFF1A73E8)
