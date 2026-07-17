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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
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
private val DayChipWidth = 72.dp
private val DayChipHeight = 120.dp

/**
 * The city detail screen: geography chips, a per-day selector, and the day's ranked activities.
 * Tapping a day re-ranks activities for that day (handled by [WeatherViewModel.onDaySelected]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailContent(
    uiState: WeatherUiState,
    onDaySelected: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = uiState.isLoadingForecast && uiState.forecast != null,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            uiState.selectedLocation?.let { GeoChipsRow(it) }

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
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.detail_pick_a_day),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(12.dp))
                DaySelectorRow(
                    days = forecast.dailyForecasts,
                    selectedIndex = uiState.selectedDayIndex,
                    onDaySelected = onDaySelected
                )

                val selectedDay = forecast.dailyForecasts.getOrNull(uiState.selectedDayIndex)
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(
                        R.string.detail_activities_for_day,
                        selectedDay?.let { isoDateToWeekday(it.date) } ?: ""
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(12.dp))

                // Slide-up + fade when the selected day changes so the re-ranking reads as new content.
                AnimatedContent(
                    targetState = uiState.selectedDayIndex to uiState.rankedActivities,
                    transitionSpec = {
                        (slideInVertically(tween(DAY_SWITCH_MS)) { it / 8 } + fadeIn(tween(DAY_SWITCH_MS)))
                            .togetherWith(fadeOut(tween(DAY_SWITCH_MS / 2)))
                    },
                    label = "day_activities"
                ) { (_, activities) ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.animateContentSize()
                    ) {
                        activities.forEach { ranked -> ActivityCard(ranked) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GeoChipsRow(location: Location) {
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val subtitle = listOfNotNull(location.admin1, location.country).joinToString(", ")
        if (subtitle.isNotBlank()) {
            InfoChip(text = subtitle)
        }
        InfoChip(
            text = stringResource(
                if (location.hasSeaAccess) R.string.chip_coastal else R.string.chip_inland
            )
        )
        location.elevation?.let {
            InfoChip(text = stringResource(R.string.chip_elevation, it.roundToInt()))
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun DaySelectorRow(
    days: List<DailyForecast>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(days) { index, day ->
            DaySelectorCard(
                day = day,
                selected = index == selectedIndex,
                onClick = { onDaySelected(index) }
            )
        }
    }
}

@Composable
private fun DaySelectorCard(
    day: DailyForecast,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Animate selection so tapping a day feels responsive rather than a hard swap.
    // Selected keeps strong primary styling for contrast; unselected uses weather pastels.
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            weatherPastelBackground(day.weatherCode)
        },
        animationSpec = tween(250),
        label = "day_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(250),
        label = "day_content"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "day_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(DayChipWidth)
            .height(DayChipHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .then(
                if (selected) Modifier
                else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(18.dp)
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Text(
            text = isoDateToWeekday(day.date),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.8f),
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = isoDateToDayOfMonth(day.date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1
        )
        Spacer(Modifier.height(8.dp))
        Icon(
            weatherCodeIcon(day.weatherCode),
            contentDescription = weatherCodeDescription(day.weatherCode),
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.temp_degrees, day.maxTemp.roundToInt()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * Soft pastel container for unselected day chips. Cloudy/fog stay on the neutral
 * surfaceVariant; other conditions get a light weather-tinted pastel.
 * Uses [MaterialTheme] luminance so Paparazzi darkTheme overrides are respected.
 */
@Composable
private fun weatherPastelBackground(weatherCode: Int): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (weatherUiCategory(weatherCode)) {
        WeatherUiCategory.CLEAR -> if (isDark) PastelSunnyDark else PastelSunnyLight
        WeatherUiCategory.RAIN -> if (isDark) PastelRainDark else PastelRainLight
        WeatherUiCategory.SNOW -> if (isDark) PastelSnowDark else PastelSnowLight
        WeatherUiCategory.THUNDERSTORM -> if (isDark) PastelThunderDark else PastelThunderLight
        WeatherUiCategory.CLOUDY -> MaterialTheme.colorScheme.surfaceVariant
    }
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
private fun ActivityCard(rankedActivity: RankedActivity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The ring sweeps up to its value on first composition and eases between values on re-rank.
            val animatedScore by animateFloatAsState(
                targetValue = rankedActivity.score / 100f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "score_ring"
            )
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedScore },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp,
                    color = when {
                        rankedActivity.score > 75 -> MaterialTheme.colorScheme.primary
                        rankedActivity.score > 40 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Text(
                    text = rankedActivity.score.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        activityIcon(rankedActivity.activity),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = rankedActivity.activity.asUiText().asString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = rankedActivity.reasonKey.asUiText(rankedActivity.reasonArgs).asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
