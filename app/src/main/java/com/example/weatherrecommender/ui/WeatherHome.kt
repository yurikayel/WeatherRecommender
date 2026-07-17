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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.TopPick
import com.example.weatherrecommender.ui.util.asUiText
import com.example.weatherrecommender.ui.util.weatherCodeDescription
import com.example.weatherrecommender.ui.util.weatherCodeIcon
import kotlin.math.roundToInt

/**
 * The home screen: a friendly greeting, search field, and a feed of population-weighted "top picks".
 */
@Composable
internal fun HomeContent(
    uiState: WeatherUiState,
    onQueryChanged: (String) -> Unit,
    onLocationSelected: (Location) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_greeting_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.home_greeting_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(16.dp))
        CustomSearchBar(
            query = uiState.query,
            onQueryChange = onQueryChanged,
            isSearching = uiState.isSearching,
            modifier = Modifier.fillMaxWidth()
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
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.home_top_picks_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(12.dp))
            TopPicksSection(
                topPicks = uiState.topPicks,
                isLoading = uiState.isLoadingTopPicks,
                onLocationSelected = onLocationSelected
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

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
            .background(MaterialTheme.colorScheme.surfaceVariant)
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

@Composable
private fun TopPicksSection(
    topPicks: List<TopPick>,
    isLoading: Boolean,
    onLocationSelected: (Location) -> Unit
) {
    // Crossfade from skeleton to content so the feed appears without a hard pop.
    Crossfade(targetState = isLoading, label = "top_picks_fade") { loading ->
        when {
            loading -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(3) {
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .height(150.dp)
                                .clip(RoundedCornerShape(20.dp))
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(topPicks) { pick ->
                        TopPickCard(pick = pick, onClick = { onLocationSelected(pick.location) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPickCard(pick: TopPick, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "pick_press_scale"
    )

    Card(
        modifier = Modifier
            .width(220.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.width(120.dp)) {
                    Text(
                        text = pick.location.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    pick.location.country?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        weatherCodeIcon(pick.weatherCode),
                        contentDescription = weatherCodeDescription(pick.weatherCode),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.temp_degrees, pick.maxTemp.roundToInt()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    activityIcon(pick.topActivity.activity),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pick.topActivity.activity.asUiText().asString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
