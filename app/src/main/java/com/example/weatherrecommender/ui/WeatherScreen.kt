package com.example.weatherrecommender.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.Location
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
 * Home and detail each embed a square map as the first section of their scroll content
 * (not a sticky overlay). Map camera/pin still come from [WeatherUiState] for continuity.
 * Modes are derived from [WeatherUiState.selectedLocation].
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
    val inDetail = uiState.selectedLocation != null
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

    Scaffold(
        topBar = {
            WeatherTopBar(
                title = uiState.selectedLocation?.name
                    ?: stringResource(R.string.app_title),
                inDetail = inDetail,
                canShare = canShare,
                shareInProgress = shareInProgress,
                onBack = onBack,
                onShare = {
                    if (needsLegacyWritePermission(context)) {
                        writeStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        shareInProgress = true
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (inDetail) {
            BackHandler(onBack = onBack)
        }
        AnimatedContent(
            targetState = inDetail,
            transitionSpec = { homeDetailTransition() },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            label = "home_detail_transition"
        ) { detail ->
            if (detail) {
                DetailContent(
                    uiState = uiState,
                    onDaySelected = onDaySelected,
                    onRefresh = onRefresh,
                    onMapTapped = onMapTapped
                )
            } else {
                HomeContent(
                    uiState = uiState,
                    onQueryChanged = onQueryChanged,
                    onLocationSelected = onLocationSelected,
                    onRefresh = onRefresh,
                    onMapTapped = onMapTapped,
                    onCurrentLocationClick = onCurrentLocationClick,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }
        }
    }

    val location = uiState.selectedLocation
    val forecast = uiState.forecast
    if (shareInProgress && location != null && forecast != null) {
        ShareWeatherCapture(
            location = location,
            days = forecast.dailyForecasts,
            tipActivity = uiState.rankedActivities.firstOrNull()?.activity,
            onComplete = { outcome ->
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherTopBar(
    title: String,
    inDetail: Boolean,
    canShare: Boolean,
    shareInProgress: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    TopAppBar(
        title = {
            Text(text = title, fontWeight = FontWeight.SemiBold)
        },
        navigationIcon = {
            if (inDetail) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.detail_back)
                    )
                }
            }
        },
        actions = {
            if (canShare) {
                IconButton(onClick = onShare, enabled = !shareInProgress) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.share_weather)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
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

private fun AnimatedContentTransitionScope<Boolean>.homeDetailTransition(): ContentTransform {
    val enterSlide = { width: Int -> width / 5 }
    val exitSlide = { width: Int -> width / 5 }
    val easing = FastOutSlowInEasing
    return if (targetState) {
        (
            slideInHorizontally(
                animationSpec = tween(TRANSITION_MS, easing = easing),
                initialOffsetX = enterSlide
            ) + fadeIn(tween(TRANSITION_MS, easing = easing))
            ).togetherWith(
            slideOutHorizontally(
                animationSpec = tween(TRANSITION_MS, easing = easing),
                targetOffsetX = { -exitSlide(it) }
            ) + fadeOut(tween(TRANSITION_MS, easing = easing))
        )
    } else {
        (
            slideInHorizontally(
                animationSpec = tween(TRANSITION_MS, easing = easing),
                initialOffsetX = { -enterSlide(it) }
            ) + fadeIn(tween(TRANSITION_MS, easing = easing))
            ).togetherWith(
            slideOutHorizontally(
                animationSpec = tween(TRANSITION_MS, easing = easing),
                targetOffsetX = exitSlide
            ) + fadeOut(tween(TRANSITION_MS, easing = easing))
        )
    }
}

private const val TRANSITION_MS = 380
