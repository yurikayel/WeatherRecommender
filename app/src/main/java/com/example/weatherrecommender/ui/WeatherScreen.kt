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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
 * A full-screen MapLibre map stays mounted while a Material 3 [BottomSheetScaffold] Crossfades
 * home vs detail. Peek height is [SHEET_PEEK_FRACTION] of the screen (~40%), so ~60% of the map
 * stays visible. The sheet drags to full screen and its content scrolls. The map instance is not
 * remounted on select/back. Modes are derived from [WeatherUiState.destination].
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // Map draws edge-to-edge under the status bar; the sheet adds status-bar padding only when
    // fully expanded. Scaffold applies horizontal + bottom safe areas.
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
        MapBottomSheetScaffold(
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
 * Full-screen map with a persistent Material 3 bottom sheet.
 * Peek is [SHEET_PEEK_FRACTION] of screen height (map ~60% visible); drag expands to full screen.
 * Home↔detail Crossfade lives in the sheet so MapLibre is not remounted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapBottomSheetScaffold(
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
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    var showInfoDialog by remember { mutableStateOf(false) }
    val sheetShape = RoundedCornerShape(topStart = SheetTopCorner, topEnd = SheetTopCorner)
    val peekHeight = LocalConfiguration.current.screenHeightDp.dp * SHEET_PEEK_FRACTION
    val sheetFullyExpanded = sheetState.currentValue == SheetValue.Expanded
    val sheetPeeked = sheetState.currentValue == SheetValue.PartiallyExpanded

    // Map-first hops need the map visible: snap back to peek on home↔detail.
    LaunchedEffect(inDetail) {
        if (sheetState.currentValue != SheetValue.PartiallyExpanded) {
            sheetState.partialExpand()
        }
    }

    val loc = uiState.selectedLocation
    if (showInfoDialog && loc != null) {
        LocationInfoDialog(
            location = loc,
            onDismiss = { showInfoDialog = false }
        )
    }

    BottomSheetScaffold(
        modifier = modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        containerColor = Color.Transparent,
        sheetPeekHeight = peekHeight,
        sheetShape = sheetShape,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetTonalElevation = 1.dp,
        sheetShadowElevation = 6.dp,
        sheetDragHandle = if (inDetail) {
            null
        } else {
            { BottomSheetDefaults.DragHandle() }
        },
        sheetContent = {
            Crossfade(
                targetState = inDetail,
                animationSpec = tween(BODY_CROSSFADE_MS, easing = FastOutSlowInEasing),
                modifier = Modifier.fillMaxWidth(),
                label = "home_detail_crossfade"
            ) { detail ->
                if (detail) {
                    DetailContent(
                        uiState = uiState,
                        onDaySelected = onDaySelected,
                        header = {
                            WeatherSheetHeader(
                                title = uiState.selectedLocation?.name.orEmpty(),
                                inDetail = true,
                                canShare = canShare,
                                shareInProgress = shareInProgress,
                                isDarkTheme = isDarkTheme,
                                sheetFullyExpanded = sheetFullyExpanded,
                                overlayOnHero = true,
                                onToggleTheme = onToggleTheme,
                                onBack = onBack,
                                onShare = onShare,
                                onInfoClick = { showInfoDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    )
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        WeatherSheetHeader(
                            title = "",
                            inDetail = false,
                            canShare = canShare,
                            shareInProgress = shareInProgress,
                            isDarkTheme = isDarkTheme,
                            sheetFullyExpanded = sheetFullyExpanded,
                            onToggleTheme = onToggleTheme,
                            onBack = onBack,
                            onShare = onShare,
                            searchQuery = uiState.query,
                            isSearching = uiState.isSearching,
                            onQueryChange = onQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 4.dp, bottom = 4.dp)
                        )
                        HomeContent(
                            uiState = uiState,
                            onLocationSelected = onLocationSelected,
                            onRefresh = onRefresh,
                            onCurrentLocationClick = onCurrentLocationClick,
                            sheetPeeked = sheetPeeked
                        )
                    }
                }
            }
        }
    ) {
        WeatherMapSection(
            camera = uiState.mapCamera,
            pin = uiState.mapPin,
            isResolvingTap = uiState.isResolvingMapTap,
            onMapTap = onMapTapped,
            interactive = !sheetFullyExpanded,
            darkTheme = isDarkTheme,
            modifier = Modifier.fillMaxSize()
        )
    }
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
/** Peek fraction of screen height. Map remains ~60% visible behind the sheet. */
private const val SHEET_PEEK_FRACTION = 0.40f
private val SheetTopCorner = 24.dp
