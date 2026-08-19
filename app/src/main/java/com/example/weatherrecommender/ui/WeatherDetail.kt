package com.example.weatherrecommender.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ScoringThresholds
import com.example.weatherrecommender.theme.PastelRainDark
import com.example.weatherrecommender.theme.PastelRainLight
import com.example.weatherrecommender.theme.PastelSnowDark
import com.example.weatherrecommender.theme.PastelSnowLight
import com.example.weatherrecommender.theme.PastelSunnyDark
import com.example.weatherrecommender.theme.PastelSunnyLight
import com.example.weatherrecommender.theme.PastelThunderDark
import com.example.weatherrecommender.theme.PastelThunderLight
import com.example.weatherrecommender.ui.util.WeatherUiCategory
import com.example.weatherrecommender.ui.util.asUiText
import com.example.weatherrecommender.ui.util.isoDateToDayOfMonth
import com.example.weatherrecommender.ui.util.isoDateToWeekday
import com.example.weatherrecommender.ui.util.weatherCodeDescription
import com.example.weatherrecommender.ui.util.weatherCodeIcon
import com.example.weatherrecommender.ui.util.weatherUiCategory
import kotlin.math.roundToInt

private const val DAY_SWITCH_MS = 320

/**
 * Detail body inside the collapsing-map sheet: geography chips, day selector, and ranked activities.
 * The map lives in [WeatherScreenContent] so it stays mounted across home↔detail.
 * Tapping a day re-ranks activities (handled by [WeatherViewModel.onDaySelected]).
 * Pull-to-refresh is home-only (bonus) so it cannot fight nested-scroll map collapse here.
 */
@Composable
internal fun DetailContent(
    uiState: WeatherUiState,
    onDaySelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        val location = uiState.selectedLocation
        val imageUrl = location?.imageUrl ?: uiState.forecast?.location?.imageUrl
        if (imageUrl != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.9f
                )
            }
        }

        if (uiState.isLoadingForecast && uiState.forecast == null) {
            Spacer(Modifier.height(16.dp))
            PremiumShimmerLoadingState()
        }

        uiState.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.error_prefix, error.asString()),
                color = MaterialTheme.colorScheme.error
            )
        }

        uiState.syncError?.let { syncError ->
            Spacer(Modifier.height(12.dp))
            SyncErrorBanner(syncError.asString())
        }

        val forecast = uiState.forecast
        if (forecast != null) {
            val selectedDay = forecast.dailyForecasts.getOrNull(uiState.selectedDayIndex)
            val selectedDayName = selectedDay?.let { isoDateToWeekday(it.date) } ?: ""
            
            Spacer(Modifier.height(12.dp))
            Text(
                text = selectedDayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(16.dp))
            
            AnimatedContent(
                targetState = uiState.selectedDayIndex to uiState.rankedActivities,
                transitionSpec = {
                    (slideInVertically(tween(DAY_SWITCH_MS)) { it / 8 } + fadeIn(tween(DAY_SWITCH_MS)))
                        .togetherWith(fadeOut(tween(DAY_SWITCH_MS / 2)))
                },
                label = "day_activities"
            ) { (_, activities) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .animateContentSize()
                ) {
                    activities.sortedByDescending { it.score }.forEach { ranked ->
                        ActivityItem(ranked)
                    }
                }
            }
            
            
            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.tab_7_day),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(16.dp))


            WeekSummarySection(
                forecast = forecast,
                weekTopActivities = uiState.weekTopActivities,
                selectedDayIndex = uiState.selectedDayIndex,
                onDaySelected = onDaySelected
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun LocationInfoDialog(
    location: Location,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = location.name)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val subtitle = listOfNotNull(location.admin1, location.country).joinToString(", ")
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    text = stringResource(
                        if (location.hasSeaAccess) R.string.chip_coastal else R.string.chip_inland
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                location.elevation?.let {
                    Text(
                        text = stringResource(R.string.chip_elevation, it.roundToInt()),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}



@Composable
private fun SyncErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(
                R.string.sync_error_with_warning,
                stringResource(R.string.sync_error_prefix, message)
            ),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}
@Composable
private fun ActivityItem(rankedActivity: RankedActivity) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                activityIcon(rankedActivity.activity),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = rankedActivity.activity.asUiText().asString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = rankedActivity.score.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    rankedActivity.score > ScoringThresholds.SCORE_HIGH -> MaterialTheme.colorScheme.primary
                    rankedActivity.score > ScoringThresholds.SCORE_MID -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
        Text(
            text = rankedActivity.reasonKey.asUiText(rankedActivity.reasonArgs).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
