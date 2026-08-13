package com.example.weatherrecommender.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.ui.map.WeatherMapSection
import kotlinx.coroutines.launch

/**
 * Entry point for the weather recommendation feature.
 * Binds the [WeatherViewModel] and observes the [WeatherUiState].
 */
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WeatherScreenContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onLocationSelected = viewModel::onLocationSelected,
        onMapTapped = viewModel::onMapTapped,
        onDaySelected = viewModel::onDaySelected,
        onBack = viewModel::onBack,
        onRefresh = viewModel::refresh,
        onCurrentLocationClick = viewModel::onCurrentLocationClick,
        onLocationPermissionResult = viewModel::onLocationPermissionResult,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme
    )
}

/**
 * Stateless, testable root of the Weather screen.
 *
 * A collapsing map (1:1 expanded → fully hidden) stays mounted while a surface sheet below
 * Crossfades home vs detail body content — no full-screen slide, and the map instance is
 * not remounted on select/back. Modes are derived from [WeatherUiState.destination].
 */
@Composable
fun WeatherScreenContent(
    uiState: WeatherUiState,
    onQueryChanged: (String) -> Unit,
    onLocationSelected: (Location) -> Unit,
    onDaySelected: (Int) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMapTapped: (Double, Double) -> Unit = { _, _ -> },
    onCurrentLocationClick: () -> Unit = {},
    onLocationPermissionResult: (Boolean) -> Unit = {},
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
) {
    val inDetail = uiState.destination is WeatherDestination.Detail
    val canShare = inDetail &&
        uiState.forecast != null &&
        uiState.forecast.dailyForecasts.isNotEmpty()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var shareInProgress by remember { mutableStateOf(false) }
    val shareFailedMessage = stringResource(R.string.share_weather_failed)
    val shareSavedMessage = stringResource(R.string.share_weather_saved_downloads)
    val shareSaveFailedMessage = stringResource(R.string.share_weather_save_failed)

    val writeStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Proceed either way — share must not depend on Downloads permission.
        shareInProgress = true
    }

    RequestLocationPermissionEffect(onLocationPermissionResult)

    // Leaving detail (e.g. back while a permission dialog was up) must cancel a pending share;
    // otherwise the stranded flag would auto-trigger a capture on the next city opened.
    LaunchedEffect(inDetail) {
        if (!inDetail) shareInProgress = false
    }

    // Map draws edge-to-edge under the status bar; the sheet header adds status-bar padding
    // only when the map is fully collapsed. Scaffold applies horizontal + bottom safe areas.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) { padding ->
        if (inDetail) {
            BackHandler(onBack = onBack)
        }
        CollapsingMapScaffold(
            uiState = uiState,
            inDetail = inDetail,
            canShare = canShare,
            shareInProgress = shareInProgress,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onBack = onBack,
            onShare = {
                if (needsLegacyWritePermission(context)) {
                    writeStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    shareInProgress = true
                }
            },
            onMapTapped = onMapTapped,
            onQueryChanged = onQueryChanged,
            onLocationSelected = onLocationSelected,
            onDaySelected = onDaySelected,
            onRefresh = onRefresh,
            onCurrentLocationClick = onCurrentLocationClick,
            modifier = Modifier.padding(padding)
        )
    }

    PendingShareCapture(
        shareInProgress = shareInProgress,
        uiState = uiState,
        onFinished = { outcome ->
            shareInProgress = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    shareOutcomeMessage(
                        outcome = outcome,
                        failed = shareFailedMessage,
                        saved = shareSavedMessage,
                        saveFailed = shareSaveFailedMessage
                    )
                )
            }
        }
    )
}

@Composable
private fun RequestLocationPermissionEffect(onLocationPermissionResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onLocationPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        when {
            hasLocationPermission(context) -> onLocationPermissionResult(true)
            else -> locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}

@Composable
private fun PendingShareCapture(
    shareInProgress: Boolean,
    uiState: WeatherUiState,
    onFinished: (ShareWeatherOutcome) -> Unit
) {
    val location = uiState.selectedLocation
    val forecast = uiState.forecast
    if (shareInProgress && location != null && forecast != null) {
        ShareWeatherCapture(
            location = location,
            days = forecast.dailyForecasts,
            selectedDayIndex = uiState.selectedDayIndex,
            rankedActivities = uiState.rankedActivities,
            onComplete = onFinished
        )
    }
}

/**
 * Collapsing map header (1:1) + rounded sheet that nested-scrolls over it.
 * Map height is animated via [MapCollapseState]; the MapLibre instance is not remounted.
 */
@Composable
private fun CollapsingMapScaffold(
    uiState: WeatherUiState,
    inDetail: Boolean,
    canShare: Boolean,
    shareInProgress: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onMapTapped: (Double, Double) -> Unit,
    onQueryChanged: (String) -> Unit,
    onLocationSelected: (Location) -> Unit,
    onDaySelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val collapse = rememberMapCollapseState(resetKey = inDetail)
    var showInfoDialog by remember { mutableStateOf(false) }

    val loc = uiState.selectedLocation
    if (showInfoDialog && loc != null) {
        LocationInfoDialog(
            location = loc,
            onDismiss = { showInfoDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(collapse.nestedScrollConnection)
    ) {
        WeatherMapSection(
            camera = uiState.mapCamera,
            pin = uiState.mapPin,
            isResolvingTap = uiState.isResolvingMapTap,
            onMapTap = onMapTapped,
            interactive = collapse.mapInteractive,
            modifier = Modifier
                .fillMaxWidth()
                .height(collapse.headerHeight)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = 1f - collapse.fraction * 0.35f }
        )

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = collapse.sheetTop),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                WeatherSheetHeader(
                    title = if (inDetail) uiState.selectedLocation?.name.orEmpty() else "",
                    inDetail = inDetail,
                    canShare = canShare,
                    shareInProgress = shareInProgress,
                    isDarkTheme = isDarkTheme,
                    mapFullyCollapsed = collapse.fraction >= 1f,
                    onToggleTheme = onToggleTheme,
                    onBack = onBack,
                    onShare = onShare,
                    searchQuery = uiState.query,
                    isSearching = uiState.isSearching,
                    onQueryChange = onQueryChanged,
                    onInfoClick = { showInfoDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 4.dp)
                )
                Crossfade(
                    targetState = inDetail,
                    animationSpec = tween(BODY_CROSSFADE_MS, easing = FastOutSlowInEasing),
                    modifier = Modifier.fillMaxSize(),
                    label = "home_detail_crossfade"
                ) { detail ->
                    if (detail) {
                        DetailContent(
                            uiState = uiState,
                            onDaySelected = onDaySelected
                        )
                    } else {
                        HomeContent(
                            uiState = uiState,
                            onLocationSelected = onLocationSelected,
                            onRefresh = onRefresh,
                            onCurrentLocationClick = onCurrentLocationClick,
                            // PTR only when the map is fully open so overscroll doesn't steal collapse.
                            mapFullyExpanded = collapse.fraction == 0f
                        )
                    }
                }
            }
        }
    }
}

private class MapCollapseState(
    val nestedScrollConnection: NestedScrollConnection,
    val headerHeight: Dp,
    val sheetTop: Dp,
    val fraction: Float,
    val mapInteractive: Boolean
)

@Composable
private fun rememberMapCollapseState(resetKey: Any): MapCollapseState {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val expandedMapHeight = screenWidthDp * MAP_ASPECT_HEIGHT / MAP_ASPECT_WIDTH
    val expandedPx = with(density) { expandedMapHeight.toPx() }
    val maxCollapsePx = expandedPx.coerceAtLeast(1f)

    var toolbarOffsetPx by remember { mutableFloatStateOf(0f) }

    // Expand the map again when switching home ↔ detail so the new mode starts with the header open.
    LaunchedEffect(resetKey) {
        toolbarOffsetPx = 0f
    }

    val nestedScrollConnection = remember(maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // Scroll up: collapse the map before the sheet content scrolls.
                if (delta < 0f) {
                    val previous = toolbarOffsetPx
                    toolbarOffsetPx = (toolbarOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    return Offset(0f, toolbarOffsetPx - previous)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                // Scroll down: expand the map after the sheet has scrolled to its top.
                if (delta > 0f) {
                    val previous = toolbarOffsetPx
                    toolbarOffsetPx = (toolbarOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    return Offset(0f, toolbarOffsetPx - previous)
                }
                return Offset.Zero
            }
        }
    }

    val headerHeight = with(density) { (expandedPx + toolbarOffsetPx).toDp() }
    val fraction = (-toolbarOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
    val sheetTop = (headerHeight - MapSheetOverlap).coerceAtLeast(0.dp)

    return MapCollapseState(
        nestedScrollConnection = nestedScrollConnection,
        headerHeight = headerHeight,
        sheetTop = sheetTop,
        fraction = fraction,
        mapInteractive = fraction < MAP_INTERACTIVE_COLLAPSE_THRESHOLD
    )
}

private fun needsLegacyWritePermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return false
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) != PackageManager.PERMISSION_GRANTED
}

private fun hasLocationPermission(context: android.content.Context): Boolean {
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

private fun shareOutcomeMessage(
    outcome: ShareWeatherOutcome,
    failed: String,
    saved: String,
    saveFailed: String
): String = when {
    !outcome.shared -> failed
    outcome.savedToDownloads -> saved
    else -> saveFailed
}

private const val BODY_CROSSFADE_MS = 280
/** Width:height = 1:1 when the collapsing map header is fully expanded. */
private const val MAP_ASPECT_WIDTH = 1f
private const val MAP_ASPECT_HEIGHT = 1f
private val MapSheetOverlap: Dp = 28.dp
private const val MAP_INTERACTIVE_COLLAPSE_THRESHOLD = 0.72f
