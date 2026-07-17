package com.example.weatherrecommender.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.weatherrecommender.ui.map.WeatherMapHeader
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
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme
    )
}

/**
 * Stateless, testable root of the Weather screen.
 *
 * Renders a **persistent map header** above home/detail content so the map survives navigation.
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

    fun startShare() {
        val needsLegacyWrite = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (needsLegacyWrite && !hasWrite) {
            writeStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            shareInProgress = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.selectedLocation?.name
                            ?: stringResource(R.string.app_title),
                        fontWeight = FontWeight.SemiBold
                    )
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
                        IconButton(
                            onClick = { startShare() },
                            enabled = !shareInProgress
                        ) {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Persistent across home ↔ detail: not inside AnimatedContent.
            WeatherMapHeader(
                camera = uiState.mapCamera,
                pin = uiState.mapPin,
                collapsed = inDetail,
                isResolvingTap = uiState.isResolvingMapTap,
                onMapTap = onMapTapped
            )

            Box(modifier = Modifier.weight(1f)) {
                if (inDetail) {
                    BackHandler(onBack = onBack)
                }
                AnimatedContent(
                    targetState = inDetail,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally(tween(TRANSITION_MS)) { it / 4 } + fadeIn(tween(TRANSITION_MS)))
                                .togetherWith(
                                    slideOutHorizontally(tween(TRANSITION_MS)) { -it / 4 } + fadeOut(tween(TRANSITION_MS))
                                )
                        } else {
                            (slideInHorizontally(tween(TRANSITION_MS)) { -it / 4 } + fadeIn(tween(TRANSITION_MS)))
                                .togetherWith(
                                    slideOutHorizontally(tween(TRANSITION_MS)) { it / 4 } + fadeOut(tween(TRANSITION_MS))
                                )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "home_detail_transition"
                ) { detail ->
                    if (detail) {
                        DetailContent(
                            uiState = uiState,
                            onDaySelected = onDaySelected,
                            onRefresh = onRefresh
                        )
                    } else {
                        HomeContent(
                            uiState = uiState,
                            onQueryChanged = onQueryChanged,
                            onLocationSelected = onLocationSelected,
                            onRefresh = onRefresh,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = onToggleTheme
                        )
                    }
                }
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
                    when {
                        !outcome.shared -> snackbarHostState.showSnackbar(shareFailedMessage)
                        outcome.savedToDownloads ->
                            snackbarHostState.showSnackbar(shareSavedMessage)
                        else -> snackbarHostState.showSnackbar(shareSaveFailedMessage)
                    }
                }
            }
        )
    }
}

private const val TRANSITION_MS = 300
