package com.example.weatherrecommender.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherrecommender.ui.util.asUiText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.ui.util.weatherCodeIcon
import com.example.weatherrecommender.domain.model.RankedActivity
import kotlin.math.roundToInt

/**
 * Entry point for the weather recommendation feature.
 * Binds the [WeatherViewModel] and observes the [WeatherUiState].
 *
 * @param viewModel The Hilt-injected ViewModel managing this screen's state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WeatherScreenContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onLocationSelected = viewModel::onLocationSelected,
        onRefresh = viewModel::refresh
    )
}

/**
 * Stateless, highly-testable UI component for the Weather screen.
 * Implements State Hoisting to ensure UI is decoupled from business logic.
 *
 * @param uiState The current data/state representation of the UI.
 * @param onQueryChanged Callback invoked when the user types in the search bar.
 * @param onLocationSelected Callback invoked when a location is picked from results.
 * @param onRefresh Callback invoked when the user pulls to refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreenContent(
    uiState: WeatherUiState,
    onQueryChanged: (String) -> Unit,
    onLocationSelected: (com.example.weatherrecommender.domain.model.Location) -> Unit,
    onRefresh: () -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            CustomSearchBar(
                query = uiState.query,
                onQueryChange = onQueryChanged,
                isSearching = uiState.isSearching,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(visible = uiState.searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    items(uiState.searchResults) { location ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = {
                                Text(
                                    stringResource(R.string.location_result_format, location.displayName),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            modifier = Modifier.clickable {
                                onLocationSelected(location)
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoadingForecast) {
                PremiumShimmerLoadingState()
            } else {
                uiState.error?.let { error ->
                    Text(
                        text = stringResource(R.string.error_prefix, error.asString()),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                uiState.forecast?.let { forecast ->
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoadingForecast,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(top = 1.dp)) { // Small padding to make it scrollable
                            Text(
                                text = forecast.location.name,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        stringResource(R.string.weather_forecast_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    uiState.syncError?.let { syncError ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.sync_error_with_warning,
                                    stringResource(R.string.sync_error_prefix, syncError.asString())
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 20.dp)
                    ) {
                        items(forecast.dailyForecasts) { daily ->
                            DailyForecastCard(daily)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        stringResource(R.string.recommended_activities_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.rankedActivities) { ranked ->
                            ActivityCard(ranked)
                        }
                    }
                }
            }
        }
        }
    }
}
}

@Composable
fun PremiumShimmerLoadingState() {
    Column {
        Box(modifier = Modifier.width(200.dp).height(50.dp).clip(CircleShape).shimmerEffect())
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.width(120.dp).height(24.dp).clip(CircleShape).shimmerEffect())
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) {
                Box(modifier = Modifier.width(80.dp).height(110.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.width(180.dp).height(24.dp).clip(CircleShape).shimmerEffect())
        Spacer(modifier = Modifier.height(16.dp))
        repeat(3) {
            Box(modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    return this.background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            } else if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear_search),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun DailyForecastCard(daily: DailyForecast) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "press_scale"
    )

    val icon = weatherCodeIcon(daily.weatherCode)

    val gradient = Brush.linearGradient(
        colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY)
    )
    
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable(interactionSource = interactionSource, indication = null) {}
            .padding(20.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = daily.date.takeLast(5), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(8.dp))
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${daily.maxTemp.roundToInt()}°", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "${daily.minTemp.roundToInt()}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ActivityCard(rankedActivity: RankedActivity) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "press_scale"
    )

    val activityIcon = when (rankedActivity.activity) {
        com.example.weatherrecommender.domain.model.RecommendedActivity.OUTDOOR_SIGHTSEEING -> Icons.Outlined.Park
        com.example.weatherrecommender.domain.model.RecommendedActivity.INDOOR_SIGHTSEEING -> Icons.Outlined.Museum
        com.example.weatherrecommender.domain.model.RecommendedActivity.SURFING -> Icons.Outlined.Surfing
        com.example.weatherrecommender.domain.model.RecommendedActivity.SKIING -> Icons.Outlined.DownhillSkiing
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .animateContentSize(animationSpec = tween(300))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {}
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { rankedActivity.score / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp,
                    color = if (rankedActivity.score > 75) MaterialTheme.colorScheme.primary else if (rankedActivity.score > 40) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Text(
                    text = rankedActivity.score.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(activityIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rankedActivity.activity.asUiText().asString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = rankedActivity.reasonKey.asUiText(rankedActivity.reasonArgs).asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
