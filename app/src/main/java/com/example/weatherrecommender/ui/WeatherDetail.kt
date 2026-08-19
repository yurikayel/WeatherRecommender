package com.example.weatherrecommender.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ScoringThresholds
import com.example.weatherrecommender.ui.util.asUiText
import kotlin.math.roundToInt

private const val DAY_SWITCH_MS = 220
private val CompactSheetThreshold = 300.dp

/**
 * Detail body inside the collapsing-map sheet: city hero, a full-width row of tall day buttons,
 * and a vertical column of ranked activities. Everything is weighted to the sheet (bottom half
 * under the 1:1 map) — no [androidx.compose.foundation.verticalScroll] / LazyColumn.
 * Tapping a day re-ranks activities (handled by [WeatherViewModel.onDaySelected]).
 * Pull-to-refresh is home-only (bonus) so it cannot fight nested-scroll map collapse here.
 */
@Composable
internal fun DetailContent(
    uiState: WeatherUiState,
    onDaySelected: (Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val compact = maxHeight < CompactSheetThreshold
        val location = uiState.selectedLocation
        val imageUrl = location?.imageUrl ?: uiState.forecast?.location?.imageUrl
        val showLoadingShimmer = uiState.isLoadingForecast && uiState.forecast == null
        val forecast = uiState.forecast

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(DetailLayout.BlockSpacing)
        ) {
            uiState.error?.let { error ->
                Text(
                    text = stringResource(R.string.error_prefix, error.asString()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            uiState.syncError?.let { syncError ->
                SyncErrorBanner(syncError.asString())
            }

            if (imageUrl != null) {
                CityHeroImage(
                    imageUrl = imageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(DetailLayout.HeroWeight)
                )
            }

            if (showLoadingShimmer) {
                PremiumShimmerLoadingState(
                    showHeroPlaceholder = imageUrl == null,
                    modifier = Modifier.weight(1f)
                )
            } else if (forecast != null) {
                WeekSummarySection(
                    forecast = forecast,
                    selectedDayIndex = uiState.selectedDayIndex,
                    onDaySelected = onDaySelected,
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(DetailLayout.DayRowWeight)
                )
                Crossfade(
                    targetState = uiState.selectedDayIndex to uiState.rankedActivities,
                    animationSpec = tween(DAY_SWITCH_MS),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(DetailLayout.ActivitiesWeight)
                        .testTag("detail_activity_list"),
                    label = "day_activities"
                ) { (_, activities) ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(DetailLayout.ActivitySpacing)
                    ) {
                        activities.forEach { ranked ->
                            ActivityItem(
                                rankedActivity = ranked,
                                compact = compact,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CityHeroImage(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(DetailLayout.HeroCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
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
            TextButton(onClick = onDismiss) {
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ActivityItem(
    rankedActivity: RankedActivity,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 8.dp)
        ) {
            Icon(
                activityIcon(rankedActivity.activity),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (compact) 18.dp else 22.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = rankedActivity.activity.asUiText().asString(),
                        style = if (compact) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = rankedActivity.score.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            rankedActivity.score > ScoringThresholds.SCORE_HIGH ->
                                MaterialTheme.colorScheme.primary
                            rankedActivity.score > ScoringThresholds.SCORE_MID ->
                                MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
                if (!compact) {
                    Text(
                        text = rankedActivity.reasonKey.asUiText(rankedActivity.reasonArgs).asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
