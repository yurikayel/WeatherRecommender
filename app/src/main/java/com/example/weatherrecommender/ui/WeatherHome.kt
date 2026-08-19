@file:Suppress("TooManyFunctions") // Extracted PlaceImageCard / feed tabs; one home sheet file.

package com.example.weatherrecommender.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.TopPick
import com.example.weatherrecommender.ui.util.asUiText
import com.example.weatherrecommender.ui.util.weatherCodeDescription
import com.example.weatherrecommender.ui.util.weatherCodeIcon
import kotlin.math.roundToInt

/**
 * Home body inside the map bottom sheet: optional current-location chip and
 * population-weighted "top picks".
 * Pull-to-refresh (assignment bonus) force-refreshes top picks (bypasses the in-memory TTL cache).
 * Gated with [Modifier.pullToRefresh] `enabled` only when the sheet is peeked **and** the
 * list is scrolled to the top — so PTR does not fight sheet expand/collapse.
 * The map lives in [WeatherScreenContent] so it stays mounted across home↔detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    uiState: WeatherUiState,
    onLocationSelected: (Location) -> Unit,
    onRefresh: () -> Unit,
    onCurrentLocationClick: () -> Unit = {},
    sheetPeeked: Boolean = true
) {
    val scrollState = rememberScrollState()
    val pullToRefreshState = rememberPullToRefreshState()
    var selectedHomeTabIndex by remember { mutableIntStateOf(0) }
    // Material3 1.3.x PullToRefreshBox has no `enabled`; use Modifier.pullToRefresh instead.
    val canPullToRefresh = sheetPeeked && scrollState.value == 0
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_sheet_body")
            .pullToRefresh(
                isRefreshing = uiState.isRefreshingTopPicks,
                state = pullToRefreshState,
                enabled = canPullToRefresh,
                onRefresh = onRefresh
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            HomeHeader(
                currentLocationCity = uiState.deviceLocation?.name,
                onCurrentLocationClick = onCurrentLocationClick
            )


            uiState.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.error_prefix, error.asString()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            AnimatedVisibility(visible = uiState.searchResults.isNotEmpty()) {
                SearchResultsList(
                    results = uiState.searchResults,
                    onLocationSelected = onLocationSelected
                )
            }

            if (uiState.searchResults.isEmpty()) {
                HomeFeedTabs(
                    selectedTabIndex = selectedHomeTabIndex,
                    onTabSelected = { selectedHomeTabIndex = it },
                    topPicks = uiState.topPicks,
                    isLoadingTopPicks = uiState.isLoadingTopPicks,
                    recentHistory = uiState.recentHistory,
                    onLocationSelected = onLocationSelected
                )
            }

            Spacer(Modifier.height(20.dp))
            MapAttributionFooter()
            Spacer(Modifier.height(16.dp))
        }
        PullToRefreshDefaults.Indicator(
            state = pullToRefreshState,
            isRefreshing = uiState.isRefreshingTopPicks,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/** Legal tile credit shown under the home feed. */
@Composable
private fun MapAttributionFooter() {
    val attribution = stringResource(R.string.map_attribution)
    Text(
        text = attribution,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = attribution }
    )
}

/** Current-location chip when GPS has resolved a city; otherwise nothing. */
@Composable
private fun HomeHeader(
    currentLocationCity: String?,
    onCurrentLocationClick: () -> Unit
) {
    if (currentLocationCity == null) return
    CurrentLocationChip(
        cityName = currentLocationCity,
        onClick = onCurrentLocationClick
    )
}

/** Tappable chip that hops to the reverse-geocoded device city. */
@Composable
private fun CurrentLocationChip(
    cityName: String,
    onClick: () -> Unit
) {
    val label = stringResource(R.string.home_current_location, cityName)
    val chipCd = stringResource(R.string.home_current_location_cd, cityName)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.semantics { contentDescription = chipCd }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Scrollable geocoding hits clipped to a max height so they do not consume the whole sheet. */
@Composable
private fun SearchResultsList(
    results: List<Location>,
    onLocationSelected: (Location) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        items(results) { location ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Text(
                        stringResource(R.string.location_result_format, location.displayName),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                modifier = Modifier.clickable { onLocationSelected(location) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
        }
    }
}

/** Top Picks / Recents tabs and the selected feed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeedTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    topPicks: List<TopPick>,
    isLoadingTopPicks: Boolean,
    recentHistory: List<Location>,
    onLocationSelected: (Location) -> Unit
) {
    Spacer(Modifier.height(24.dp))
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Tab(
            selected = selectedTabIndex == 0,
            onClick = { onTabSelected(0) },
            text = { Text(stringResource(R.string.tab_top_picks)) }
        )
        if (recentHistory.isNotEmpty()) {
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { onTabSelected(1) },
                text = { Text(stringResource(R.string.tab_recent)) }
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    when {
        selectedTabIndex == 0 -> TopPicksSection(
            topPicks = topPicks,
            isLoading = isLoadingTopPicks,
            onLocationSelected = onLocationSelected
        )
        selectedTabIndex == 1 && recentHistory.isNotEmpty() -> HistorySection(
            history = recentHistory,
            onLocationSelected = onLocationSelected
        )
    }
}

/** Shared 110dp city card: optional photo backdrop, press scale, and caller overlay. */
@Composable
private fun PlaceImageCard(
    imageUrl: String?,
    onClick: () -> Unit,
    pressLabel: String,
    contentDescription: String? = null,
    overlay: @Composable (contentColor: Color, hasImage: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = pressLabel
    )
    val hasImage = imageUrl != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasImage) {
                PlaceCardBackdrop(imageUrl = imageUrl)
            }
            val contentColor = if (hasImage) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            overlay(contentColor, hasImage)
        }
    }
}

/** Crops [imageUrl] and darkens it so overlay text stays readable. */
@Composable
private fun PlaceCardBackdrop(imageUrl: String?) {
    AsyncImage(
        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.2f),
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
    )
}

/** Shimmer, empty copy, or a column of [TopPickCard]s. */
@Composable
private fun TopPicksSection(
    topPicks: List<TopPick>,
    isLoading: Boolean,
    onLocationSelected: (Location) -> Unit
) {
    Crossfade(targetState = isLoading, label = "top_picks_fade") { loading ->
        when {
            loading -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .shimmerEffect()
                        )
                    }
                }
            }

            topPicks.isEmpty() -> {
                Text(
                    text = stringResource(R.string.home_top_picks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    topPicks.forEach { pick ->
                        TopPickCard(pick = pick, onClick = { onLocationSelected(pick.location) })
                    }
                }
            }
        }
    }
}

/** Featured city card: name, top activity, and today's weather. */
@Composable
private fun TopPickCard(pick: TopPick, onClick: () -> Unit) {
    PlaceImageCard(
        imageUrl = pick.location.imageUrl,
        onClick = onClick,
        pressLabel = "pick_press_scale"
    ) { contentColor, hasImage ->
        val iconTint = if (hasImage) Color.White else MaterialTheme.colorScheme.primary
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopPickCardCopy(pick = pick, contentColor = contentColor, iconTint = iconTint)
            TopPickCardWeather(pick = pick, contentColor = contentColor)
        }
    }
}

/** Left column of a top-pick card: city, region, and best activity. */
@Composable
private fun RowScope.TopPickCardCopy(pick: TopPick, contentColor: Color, iconTint: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(end = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = pick.location.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val subtitle = listOfNotNull(pick.location.admin1, pick.location.country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                activityIcon(pick.topActivity.activity),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = pick.topActivity.activity.asUiText().asString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Right column of a top-pick card: weather icon and high temperature. */
@Composable
private fun TopPickCardWeather(pick: TopPick, contentColor: Color) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            weatherCodeIcon(pick.weatherCode),
            contentDescription = weatherCodeDescription(pick.weatherCode),
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.temp_degrees, pick.maxTemp.roundToInt()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1
        )
    }
}

/** Vertical list of recently viewed cities. */
@Composable
private fun HistorySection(
    history: List<Location>,
    onLocationSelected: (Location) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        history.forEach { location ->
            HistoryCard(location = location, onClick = { onLocationSelected(location) })
        }
    }
}

/** History row using [PlaceImageCard] with city name and region only. */
@Composable
private fun HistoryCard(location: Location, onClick: () -> Unit) {
    val openAgainCd = stringResource(R.string.home_history_item_cd, location.displayName)
    PlaceImageCard(
        imageUrl = location.imageUrl,
        onClick = onClick,
        pressLabel = "history_press_scale",
        contentDescription = openAgainCd
    ) { contentColor, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOfNotNull(location.admin1, location.country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
