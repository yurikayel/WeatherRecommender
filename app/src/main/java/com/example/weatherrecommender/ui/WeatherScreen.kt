package com.example.weatherrecommender.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
 * Renders one of two modes based on [WeatherUiState.selectedLocation]: a friendly home feed of
 * suggestions, or a per-day detail view for the chosen city.
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
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
) {
    val inDetail = uiState.selectedLocation != null
    val canShare = inDetail &&
        uiState.forecast != null &&
        uiState.forecast.dailyForecasts.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var shareInProgress by remember { mutableStateOf(false) }
    val shareFailedMessage = stringResource(R.string.share_weather_failed)

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
                            onClick = { shareInProgress = true },
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
        Box(modifier = Modifier.padding(padding)) {
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

    val location = uiState.selectedLocation
    val forecast = uiState.forecast
    if (shareInProgress && location != null && forecast != null) {
        ShareWeatherCapture(
            location = location,
            days = forecast.dailyForecasts,
            tipActivity = uiState.rankedActivities.firstOrNull()?.activity,
            onComplete = { success ->
                shareInProgress = false
                if (!success) {
                    scope.launch {
                        snackbarHostState.showSnackbar(shareFailedMessage)
                    }
                }
            }
        )
    }
}

private const val TRANSITION_MS = 300
